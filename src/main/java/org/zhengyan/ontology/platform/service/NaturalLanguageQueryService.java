package org.zhengyan.ontology.platform.service;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;

import java.time.Duration;
import java.util.List;

@Service
public class NaturalLanguageQueryService {

    private static final Logger log = LoggerFactory.getLogger(NaturalLanguageQueryService.class);

    private final EngineRegistry engineRegistry;
    private final SparqlTemplateGenerator templateGenerator;
    private final OntologySchemaProvider schemaProvider;
    private final ChatLanguageModel llm;
    private final boolean llmAvailable;

    public NaturalLanguageQueryService(
            EngineRegistry engineRegistry,
            SparqlTemplateGenerator templateGenerator,
            OntologySchemaProvider schemaProvider,
            @Value("${ontology.nlq.llm.api-key:}") String apiKey,
            @Value("${ontology.nlq.llm.model:gpt-4o-mini}") String model,
            @Value("${ontology.nlq.llm.base-url:}") String baseUrl) {
        this.engineRegistry = engineRegistry;
        this.templateGenerator = templateGenerator;
        this.schemaProvider = schemaProvider;

        if (apiKey != null && !apiKey.isBlank() && !"sk-placeholder".equals(apiKey)) {
            var builder = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(model)
                    .timeout(Duration.ofSeconds(30))
                    .maxRetries(1);
            if (baseUrl != null && !baseUrl.isBlank()) {
                builder.baseUrl(baseUrl);
            }
            this.llm = builder.build();
            this.llmAvailable = true;
            log.info("NLQ LLM initialized: model={}", model);
        } else {
            this.llm = null;
            this.llmAvailable = false;
            log.info("NLQ running in template-only mode");
        }
    }

    public NlqResult answer(String tenantId, String question) throws Exception {
        OntologyEngine engine = engineRegistry.get(tenantId);

        String sparql = generateSparql(tenantId, question);
        String mode = determineMode();

        log.info("NLQ [{}] mode={}: '{}'", tenantId, mode, question);

        SparqlQueryResult queryResult = engine.executeQuery(sparql);
        return NlqResult.fromSparqlResult(question, sparql, queryResult, mode);
    }

    private String generateSparql(String tenantId, String question) {
        if (llmAvailable) {
            try {
                String schema = schemaProvider.getSchemaForTenant(tenantId);
                String prompt = buildLlmPrompt(tenantId, schema, question);
                UserMessage userMsg = UserMessage.from(prompt);
                ChatRequest request = ChatRequest.builder()
                        .messages(List.of(userMsg))
                        .build();
                ChatResponse response = llm.chat(request);
                String sparql = extractSparql(response.aiMessage().text());
                if (sparql != null && !sparql.isBlank()) {
                    return sparql;
                }
            } catch (Exception e) {
                log.warn("LLM call failed ({}), falling back to templates", e.getClass().getSimpleName());
            }
        }

        return templateGenerator.generate(tenantId, question)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Could not generate SPARQL for question."));
    }

    private String buildLlmPrompt(String tenantId, String schema, String question) {
        return """
                You are a SPARQL query generator for an OBDA system.
                Ontology schema for tenant "%s":
                %s
                Rules:
                - Use the correct PREFIX
                - Only generate SELECT queries
                - Return ONLY the SPARQL query, no explanation
                Natural language question: %s
                SPARQL:
                """.formatted(tenantId, schema, question);
    }

    private String extractSparql(String response) {
        if (response == null || response.isBlank()) return null;
        String trimmed = response.trim();
        int idx = trimmed.indexOf("SELECT ");
        if (idx < 0) idx = trimmed.indexOf("select ");
        if (idx < 0) idx = trimmed.indexOf("PREFIX ");
        if (idx >= 0) trimmed = trimmed.substring(idx);
        int endIdx = trimmed.indexOf("```");
        if (endIdx >= 0) trimmed = trimmed.substring(0, endIdx);
        trimmed = trimmed.replace("```sparql", "").replace("```", "").trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String determineMode() {
        return llmAvailable ? "llm" : "template";
    }
}
