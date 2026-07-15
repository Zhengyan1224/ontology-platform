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
import org.zhengyan.ontology.platform.service.RuleTriggerService;
import org.zhengyan.ontology.platform.model.QueryHistoryEntry;
import org.zhengyan.ontology.platform.repository.QueryHistoryRepository;
import org.zhengyan.ontology.platform.service.SparqlResultFormat;
import org.zhengyan.ontology.platform.service.SparqlResultFormatter;

import io.micrometer.observation.annotation.Observed;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final QueryHistoryRepository queryHistoryRepository;
    private final RuleTriggerService ruleTriggerService;

    public SparqlController(EngineRegistry engineRegistry, AuditService auditService,
                            MetricsService metricsService, SparqlResultFormatter resultFormatter,
                            CachedSparqlService cachedSparqlService,
                            QueryHistoryRepository queryHistoryRepository,
                            RuleTriggerService ruleTriggerService) {
        this.engineRegistry = engineRegistry;
        this.auditService = auditService;
        this.metricsService = metricsService;
        this.resultFormatter = resultFormatter;
        this.cachedSparqlService = cachedSparqlService;
        this.queryHistoryRepository = queryHistoryRepository;
        this.ruleTriggerService = ruleTriggerService;
    }

    @Observed(name = "sparql.execute")
    @PostMapping(value = "/tenants/{tenantId}/sparql",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object executeSparql(
            @PathVariable String tenantId,
            @Valid @RequestBody SparqlQueryRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String acceptHeader,
            HttpServletResponse response) {
        return doExecute(tenantId, request.getQuery(), acceptHeader, response);
    }

    @Observed(name = "sparql.execute.direct")
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
            int resultCount = result.isBooleanResult() ? 1 :
                    result.isGraphResult() ? result.getGraphModel().size() : result.getResults().size();
            auditService.recordSparqlQuery(tenantId, sparql, result.getTranslatedSql(),
                    elapsed, true, null, resultCount);
            metricsService.recordQuery(tenantId, elapsed, true);
            recordQueryHistory(tenantId, sparql, elapsed);
            ruleTriggerService.onSparqlQuery(tenantId, sparql, elapsed, resultCount);

            if (result.isBooleanResult()) {
                if (format.isGraphFormat()) {
                    response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
                    return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
                }
                if (format == SparqlResultFormat.JSON) {
                    return ResponseEntity.ok(result);
                }
                if (format != SparqlResultFormat.SPARQL_JSON) {
                    response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
                    return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
                }
                response.setContentType(format.getMediaType().toString());
                return (StreamingResponseBody) out -> {
                    try {
                        resultFormatter.writeBooleanResult(format, result, out);
                    } catch (Exception e) {
                        throw new OntologyPlatformException("Failed to write boolean result", 500, "WRITE_ERROR", e);
                    }
                };
            }

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

    private void recordQueryHistory(String tenantId, String sparql, long executionTimeMs) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Long apiKeyId = null;
            if (auth != null && auth.getDetails() instanceof Map) {
                Object id = ((Map<?, ?>) auth.getDetails()).get("apiKeyId");
                if (id instanceof Number) {
                    apiKeyId = ((Number) id).longValue();
                }
            }
            QueryHistoryEntry entry = new QueryHistoryEntry(tenantId, apiKeyId,
                    sparql.length() > 10000 ? sparql.substring(0, 10000) : sparql,
                    executionTimeMs);
            queryHistoryRepository.save(entry);
        } catch (Exception ignored) {
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
            int resultCount = result.isBooleanResult() ? 1 :
                    result.isGraphResult() ? result.getGraphModel().size() : result.getResults().size();
            auditService.recordSparqlQuery(tenantId, sparql, result.getTranslatedSql(),
                    elapsed, true, null, resultCount);
            metricsService.recordQuery(tenantId, elapsed, true);
            recordQueryHistory(tenantId, sparql, elapsed);
            ruleTriggerService.onSparqlQuery(tenantId, sparql, elapsed, resultCount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            auditService.recordSparqlQuery(tenantId, sparql, null,
                    elapsed, false, e.getMessage(), 0);
            metricsService.recordQuery(tenantId, elapsed, false);
            throw OntologyPlatformException.queryError(e.getMessage(), e);
        }
    }

    @Observed(name = "sparql.explain")
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
