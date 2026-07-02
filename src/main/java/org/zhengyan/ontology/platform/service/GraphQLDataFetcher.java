package org.zhengyan.ontology.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class GraphQLDataFetcher {

    private final EngineRegistry engineRegistry;
    private final NaturalLanguageQueryService nlqService;
    private final ObjectMapper objectMapper;

    public GraphQLDataFetcher(EngineRegistry engineRegistry,
                              NaturalLanguageQueryService nlqService,
                              ObjectMapper objectMapper) {
        this.engineRegistry = engineRegistry;
        this.nlqService = nlqService;
        this.objectMapper = objectMapper;
    }

    @QueryMapping
    public Map<String, Object> sparql(@Argument String tenantId, @Argument String query) throws Exception {
        OntologyEngine engine = engineRegistry.get(tenantId);
        SparqlQueryResult result = engine.executeQuery(query);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("variables", result.getVariables());
        map.put("results", result.getResults().stream()
                .map(row -> Map.of("values", row))
                .toList());
        map.put("executionTimeMs", result.getExecutionTimeMs());
        map.put("translatedSql", result.getTranslatedSql());
        return map;
    }

    @QueryMapping
    public Map<String, Object> nlq(@Argument String tenantId, @Argument String question) throws Exception {
        NlqResult result = nlqService.answer(tenantId, question);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("question", result.getQuestion());
        map.put("sparql", result.getSparql());
        map.put("mode", result.getMode());
        map.put("variables", result.getVariables());
        map.put("results", result.getResults().stream()
                .map(row -> Map.of("values", row))
                .toList());
        map.put("executionTimeMs", result.getExecutionTimeMs());
        return map;
    }
}
