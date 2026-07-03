package org.zhengyan.ontology.platform.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.exception.OntologyPlatformException;
import org.zhengyan.ontology.platform.model.SparqlQueryRequest;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.zhengyan.ontology.platform.service.AuditService;
import org.zhengyan.ontology.platform.service.CachedSparqlService;
import org.zhengyan.ontology.platform.service.MetricsService;
import org.zhengyan.ontology.platform.service.SparqlResultFormat;
import org.zhengyan.ontology.platform.service.SparqlResultFormatter;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SparqlController {

    private final EngineRegistry engineRegistry;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final SparqlResultFormatter resultFormatter;
    private final CachedSparqlService cachedSparqlService;

    public SparqlController(EngineRegistry engineRegistry, AuditService auditService,
                            MetricsService metricsService, SparqlResultFormatter resultFormatter,
                            CachedSparqlService cachedSparqlService) {
        this.engineRegistry = engineRegistry;
        this.auditService = auditService;
        this.metricsService = metricsService;
        this.resultFormatter = resultFormatter;
        this.cachedSparqlService = cachedSparqlService;
    }

    @PostMapping(value = "/tenants/{tenantId}/sparql",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object executeSparql(
            @PathVariable String tenantId,
            @Valid @RequestBody SparqlQueryRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String acceptHeader,
            HttpServletResponse response) {
        return doExecute(tenantId, request.getQuery(), acceptHeader, response);
    }

    @PostMapping(value = "/tenants/{tenantId}/sparql",
            consumes = "application/sparql-query",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SparqlQueryResult> executeSparqlDirect(
            @PathVariable String tenantId,
            @RequestBody String sparqlQuery) {
        return doExecuteJson(tenantId, sparqlQuery);
    }

    private Object doExecute(String tenantId, String sparql, String acceptHeader,
                             HttpServletResponse response) {
        OntologyEngine engine = engineRegistry.get(tenantId);
        if (!engine.isHealthy()) {
            throw OntologyPlatformException.engineNotReady(tenantId);
        }

        SparqlResultFormat format = SparqlResultFormat.fromAccept(acceptHeader)
                .orElse(null);

        if (format == null) {
            response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
        }

        long start = System.currentTimeMillis();
        try {
            SparqlQueryResult result = cachedSparqlService.executeQuery(tenantId, sparql);
            long elapsed = System.currentTimeMillis() - start;
            int resultCount = result.isGraphResult() ? result.getGraphModel().size() : result.getResults().size();
            auditService.recordSparqlQuery(tenantId, sparql, result.getTranslatedSql(),
                    elapsed, true, null, resultCount);
            metricsService.recordQuery(tenantId, elapsed, true);

            if (result.isGraphResult()) {
                if (!format.isGraphFormat()) {
                    response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
                    return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
                }
                response.setContentType(format.getMediaType().toString());
                return (StreamingResponseBody) out -> {
                    try {
                        resultFormatter.writeGraphResult(format, result.getGraphModel(), out);
                    } catch (Exception e) {
                        throw new OntologyPlatformException("Failed to write graph result", 500, "WRITE_ERROR", e);
                    }
                };
            }

            if (format.isGraphFormat()) {
                response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
                return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
            }

            if (format == SparqlResultFormat.JSON) {
                return ResponseEntity.ok(result);
            }

            response.setContentType(format.getMediaType().toString());

            return (StreamingResponseBody) out -> {
                try {
                    resultFormatter.writeTupleResult(format, result, out);
                } catch (Exception e) {
                    throw new OntologyPlatformException("Failed to write tuple result", 500, "WRITE_ERROR", e);
                }
            };
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            auditService.recordSparqlQuery(tenantId, sparql, null,
                    elapsed, false, e.getMessage(), 0);
            metricsService.recordQuery(tenantId, elapsed, false);
            throw OntologyPlatformException.queryError(e.getMessage(), e);
        }
    }

    private ResponseEntity<SparqlQueryResult> doExecuteJson(String tenantId, String sparql) {
        OntologyEngine engine = engineRegistry.get(tenantId);
        if (!engine.isHealthy()) {
            throw OntologyPlatformException.engineNotReady(tenantId);
        }

        long start = System.currentTimeMillis();
        try {
            SparqlQueryResult result = cachedSparqlService.executeQuery(tenantId, sparql);
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
