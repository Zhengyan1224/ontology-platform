package org.zhengyan.ontology.platform.service;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.yaml.snakeyaml.Yaml;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Service
public class NaturalLanguageQueryService {

    private static final Logger log = LoggerFactory.getLogger(NaturalLanguageQueryService.class);
    private static final String DEFAULT_PROMPT = """
            You are a SPARQL query generator for an OBDA system.
            Ontology schema for tenant "%s":
            %s
            Rules:
            - Use the correct PREFIX
            - Only generate SELECT queries
            - Return ONLY the SPARQL query, no explanation
            Natural language question: %s
            SPARQL:
            """;

    private final EngineRegistry engineRegistry;
    private final SparqlTemplateGenerator templateGenerator;
    private final OntologySchemaProvider schemaProvider;
    private final ResourceLoader resourceLoader;
    private final SessionManager sessionManager;
    private final ChatLanguageModel llm;
    private final boolean llmAvailable;

    public NaturalLanguageQueryService(
            EngineRegistry engineRegistry,
            SparqlTemplateGenerator templateGenerator,
            OntologySchemaProvider schemaProvider,
            ResourceLoader resourceLoader,
            SessionManager sessionManager,
            @Value("${ontology.nlq.llm.api-key:}") String apiKey,
            @Value("${ontology.nlq.llm.model:gpt-4o-mini}") String model,
            @Value("${ontology.nlq.llm.base-url:}") String baseUrl) {
        this.engineRegistry = engineRegistry;
        this.templateGenerator = templateGenerator;
        this.schemaProvider = schemaProvider;
        this.resourceLoader = resourceLoader;
        this.sessionManager = sessionManager;

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
        return answer(tenantId, question, null);
    }

    public NlqResult answer(String tenantId, String question, String sessionId) throws Exception {
        SessionManager.Session session = sessionManager.getOrCreate(sessionId);

        OntologyEngine engine = engineRegistry.get(tenantId);
        String sparql = generateSparql(tenantId, question, session);
        String mode = determineMode();

        log.info("NLQ [{}] mode={}: '{}'", tenantId, mode, question);

        SparqlQueryResult queryResult = engine.executeQuery(sparql);

        if (session != null) {
            session.addHistory(question, sparql);
        }

        return NlqResult.fromSparqlResult(question, sparql, queryResult, mode);
    }

    public void streamAnswer(String tenantId, String question, String sessionId, SseEmitter emitter) {
        SessionManager.Session session = sessionManager.getOrCreate(sessionId);

        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().name("status").data(Map.of("stage", "translating")));

                long start = System.currentTimeMillis();
                OntologyEngine engine = engineRegistry.get(tenantId);
                String sparql = generateSparql(tenantId, question, session);
                String mode = determineMode();

                if (sparql == null || sparql.isBlank()) {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", "Could not generate SPARQL")));
                    emitter.complete();
                    return;
                }

                if (session != null) {
                    session.addHistory(question, sparql);
                }

                emitter.send(SseEmitter.event().name("sparql").data(Map.of("sparql", sparql)));

                emitter.send(SseEmitter.event().name("status").data(Map.of("stage", "executing")));

                SparqlQueryResult queryResult = engine.executeQuery(sparql);
                long elapsed = System.currentTimeMillis() - start;

                emitter.send(SseEmitter.event().name("result").data(NlqResult.fromSparqlResult(
                        question, sparql, queryResult, mode)));

                emitter.send(SseEmitter.event().name("status").data(Map.of("stage", "formatting")));

                emitter.send(SseEmitter.event().name("complete").data(Map.of()));

                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", e.getMessage())));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        }, Executors.newCachedThreadPool());
    }

    private String generateSparql(String tenantId, String question, SessionManager.Session session) {
        if (llmAvailable) {
            try {
                String schema = schemaProvider.getSchemaForTenant(tenantId);
                String prompt = buildLlmPrompt(tenantId, schema, question, session);
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

    private String buildLlmPrompt(String tenantId, String schema, String question,
                                   SessionManager.Session session) {
        String template = loadPromptTemplate();
        String examples = loadFewShotExamples(tenantId);
        String history = buildConversationHistory(session);
        String prompt = template
                .replace("{{tenantId}}", tenantId)
                .replace("{{schema}}", schema)
                .replace("{{question}}", question)
                .replace("{{examples}}", examples)
                .replace("{{history}}", history);
        return prompt;
    }

    private String buildConversationHistory(SessionManager.Session session) {
        if (session == null || session.getHistory().isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Conversation history:\n");
        for (SessionManager.HistoryEntry entry : session.getHistory()) {
            sb.append("Q: \"").append(entry.getQuestion()).append("\"\n");
            sb.append("A: ").append(entry.getSparql()).append("\n\n");
        }
        return sb.toString();
    }

    private String loadPromptTemplate() {
        try {
            Resource resource = resourceLoader.getResource("classpath:nlq-templates/prompt-template.txt");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return new String(is.readAllBytes());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load prompt template, using default: {}", e.getMessage());
        }
        return DEFAULT_PROMPT;
    }

    @SuppressWarnings("unchecked")
    private String loadFewShotExamples(String tenantId) {
        try {
            String location = "classpath:nlq-templates/" + tenantId + "-examples.yml";
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) return "";
            try (InputStream is = resource.getInputStream()) {
                Yaml yaml = new Yaml();
                Map<String, Object> root = yaml.load(is);
                if (root == null) return "";
                Object examplesObj = root.get("examples");
                if (!(examplesObj instanceof List)) return "";
                List<Map<String, String>> examples = (List<Map<String, String>>) examplesObj;
                if (examples.isEmpty()) return "";

                StringBuilder sb = new StringBuilder("Examples:\n");
                for (Map<String, String> ex : examples) {
                    sb.append("Q: \"").append(ex.get("question")).append("\"\n");
                    sb.append("SPARQL:\n```sparql\n").append(ex.get("sparql")).append("\n```\n\n");
                }
                return sb.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to load few-shot examples for '{}': {}", tenantId, e.getMessage());
            return "";
        }
    }

    private String extractSparql(String response) {
        if (response == null || response.isBlank()) return null;
        String trimmed = response.trim();

        int fenceStart = trimmed.indexOf("```sparql");
        if (fenceStart >= 0) {
            trimmed = trimmed.substring(fenceStart + 9);
            int fenceEnd = trimmed.indexOf("```");
            if (fenceEnd >= 0) trimmed = trimmed.substring(0, fenceEnd);
            return validateSparql(trimmed.trim());
        }

        fenceStart = trimmed.indexOf("```");
        if (fenceStart >= 0) {
            trimmed = trimmed.substring(fenceStart + 3);
            int fenceEnd = trimmed.indexOf("```");
            if (fenceEnd >= 0) trimmed = trimmed.substring(0, fenceEnd);
            return validateSparql(trimmed.trim());
        }

        int idx = trimmed.indexOf("SELECT ");
        if (idx < 0) idx = trimmed.indexOf("select ");
        if (idx < 0) idx = trimmed.indexOf("PREFIX ");
        if (idx >= 0) trimmed = trimmed.substring(idx);
        int endIdx = trimmed.indexOf("```");
        if (endIdx >= 0) trimmed = trimmed.substring(0, endIdx);
        trimmed = trimmed.replace("```sparql", "").replace("```", "").trim();
        return validateSparql(trimmed);
    }

    private String validateSparql(String sparql) {
        if (sparql == null || sparql.isBlank()) return null;
        String upper = sparql.toUpperCase();
        if (upper.contains("SELECT") || upper.contains("CONSTRUCT")) {
            return sparql;
        }
        log.warn("Extracted SPARQL does not contain SELECT or CONSTRUCT: {}", sparql);
        return null;
    }

    private String determineMode() {
        return llmAvailable ? "llm" : "template";
    }
}
