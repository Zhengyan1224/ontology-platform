package org.zhengyan.ontology.platform.controller;

import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.model.SparqlQueryRequest;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.zhengyan.ontology.platform.exception.OntologyPlatformException;
import org.zhengyan.ontology.platform.service.AuditService;
import org.zhengyan.ontology.platform.service.MetricsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SparqlController {

    private final EngineRegistry engineRegistry;
    private final AuditService auditService;
    private final MetricsService metricsService;

    public SparqlController(EngineRegistry engineRegistry, AuditService auditService,
                            MetricsService metricsService) {
        this.engineRegistry = engineRegistry;
        this.auditService = auditService;
        this.metricsService = metricsService;
    }

    @PostMapping(value = "/tenants/{tenantId}/sparql",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SparqlQueryResult> executeSparql(
            @PathVariable String tenantId,
            @Valid @RequestBody SparqlQueryRequest request) {
        return doExecute(tenantId, request.getQuery());
    }

    @PostMapping(value = "/tenants/{tenantId}/sparql",
            consumes = "application/sparql-query",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SparqlQueryResult> executeSparqlDirect(
            @PathVariable String tenantId,
            @RequestBody String sparqlQuery) {
        return doExecute(tenantId, sparqlQuery);
    }

    private ResponseEntity<SparqlQueryResult> doExecute(String tenantId, String sparql) {
        OntologyEngine engine = engineRegistry.get(tenantId);
        if (!engine.isHealthy()) {
            throw OntologyPlatformException.engineNotReady(tenantId);
        }

        long start = System.currentTimeMillis();
        try {
            SparqlQueryResult result = engine.executeQuery(sparql);
            long elapsed = System.currentTimeMillis() - start;
            auditService.recordSparqlQuery(tenantId, sparql, result.getTranslatedSql(),
                    elapsed, true, null, result.getResults().size());
            metricsService.recordQuery(tenantId, elapsed, true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            auditService.recordSparqlQuery(tenantId, sparql, null,
                    elapsed, false, e.getMessage(), 0);
            metricsService.recordQuery(tenantId, elapsed, false);
            throw OntologyPlatformException.queryError(e.getMessage(), e);
        }
    }

    @PostMapping("/tenants/{tenantId}/sparql/explain")
    public ResponseEntity<Map<String, Object>> explain(
            @PathVariable String tenantId,
            @RequestBody @Valid ExplainRequest request) {
        OntologyEngine engine = engineRegistry.get(tenantId);
        try {
            String sql = engine.translateToSql(request.getQuery());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sparql", request.getQuery());
            result.put("sql", sql);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            throw OntologyPlatformException.queryError(e.getMessage(), e);
        }
    }

    public static class ExplainRequest {
        @NotBlank
        private String query;

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
    }
}
