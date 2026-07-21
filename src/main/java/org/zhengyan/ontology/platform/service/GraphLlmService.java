package org.zhengyan.ontology.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GraphLlmService {

    private static final Logger log = LoggerFactory.getLogger(GraphLlmService.class);

    private static final String SUGGEST_PROMPT = """
            You are an ontology engineer. Given a list of OWL classes and their existing "subClassOf" relationships, suggest additional "subClassOf" relationships between existing classes.

            Current classes:
            %s

            Existing subClassOf relationships:
            %s

            TASK: Suggest new subClassOf relationships between the classes listed above.
            
            CRITICAL RULES:
            1. ONLY use class names EXACTLY as listed in "Current classes". Do NOT invent or suggest new classes.
            2. Each suggestion must have a child class that IS-A (subclass of) the parent class.
            3. Only suggest relationships that are semantically meaningful and correct.
            4. Do NOT suggest relationships that already exist.
            5. Provide a brief reason for each suggestion (1 sentence in Chinese).
            
            Return ONLY a JSON object with this exact structure, no other text:
            {
              "suggestions": [
                { "child": "Author", "parent": "Person", "reason": "作者是一个人" },
                { "child": "EBook", "parent": "Book", "reason": "电子书是一种特殊的书" }
              ]
            }
            """;

    private static final String FALLBACK_RESPONSE = """
            {
              "suggestions": []
            }
            """;

    private final OntologyGraphService ontologyGraphService;
    private final ChatLanguageModel llm;
    private final boolean llmAvailable;

    public GraphLlmService(
            OntologyGraphService ontologyGraphService,
            @Value("${ontology.nlq.llm.api-key:}") String apiKey,
            @Value("${ontology.nlq.llm.model:gpt-4o-mini}") String model,
            @Value("${ontology.nlq.llm.base-url:}") String baseUrl) {
        this.ontologyGraphService = ontologyGraphService;

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
            log.info("Graph LLM service initialized: model={}", model);
        } else {
            this.llm = null;
            this.llmAvailable = false;
            log.info("Graph LLM service running in fallback mode (no API key)");
        }
    }

    public Map<String, Object> suggestAxioms(String tenantId, Map<String, Object> axiomConfig) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("llmAvailable", llmAvailable);

        if (!llmAvailable) {
            result.put("error", "LLM not available — configure LLM_API_KEY environment variable");
            result.put("suggestions", List.of());
            return result;
        }

        try {
            Map<String, Object> graphData = ontologyGraphService.getGraph(tenantId);
            String classList = buildClassList(graphData);
            String existingAxioms = buildExistingAxiomsText(axiomConfig);
            String prompt = String.format(SUGGEST_PROMPT, classList, existingAxioms);

            UserMessage userMsg = UserMessage.from(prompt);
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(userMsg))
                    .build();
            ChatResponse response = llm.chat(request);
            String text = response.aiMessage().text();

            List<Map<String, Object>> suggestions = parseSuggestions(text);
            result.put("suggestions", suggestions);
            return result;
        } catch (Exception e) {
            log.warn("Graph LLM suggestion failed for tenant [{}]: {}", tenantId, e.getMessage());
            result.put("error", e.getMessage());
            result.put("suggestions", List.of());
            return result;
        }
    }

    private String buildClassList(Map<String, Object> graphData) {
        List<Map<String, Object>> nodes = castList(graphData.get("nodes"));
        if (nodes == null || nodes.isEmpty()) return "(no classes)";

        return nodes.stream()
                .filter(n -> "class".equals(n.get("type")))
                .map(n -> {
                    String name = n.get("name") != null ? n.get("name").toString() : n.get("id").toString();
                    return "  - " + name;
                })
                .distinct()
                .sorted()
                .collect(Collectors.joining("\n"));
    }

    private String buildExistingAxiomsText(Map<String, Object> axiomConfig) {
        List<Map<String, Object>> subClassOf = castList(axiomConfig.get("subClassOf"));
        if (subClassOf == null || subClassOf.isEmpty()) {
            return "  (none)";
        }
        return subClassOf.stream()
                .map(a -> "  - " + a.get("child") + " rdfs:subClassOf " + a.get("parent"))
                .collect(Collectors.joining("\n"));
    }

    private List<Map<String, Object>> parseSuggestions(String text) {
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int start = cleaned.indexOf('\n');
            int end = cleaned.lastIndexOf("```");
            if (end > start) {
                cleaned = cleaned.substring(start, end).trim();
            }
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> root = mapper.readValue(cleaned,
                    new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> suggestions = castList(root.get("suggestions"));
            return suggestions != null ? suggestions : List.of();
        } catch (Exception e) {
            log.warn("Failed to parse LLM suggestions: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object obj) {
        if (obj instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map)) return List.of();
            }
            return (List<Map<String, Object>>) list;
        }
        return null;
    }
}
