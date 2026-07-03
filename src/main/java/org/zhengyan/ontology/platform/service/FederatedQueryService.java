package org.zhengyan.ontology.platform.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.config.FederatedQueryProperties;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FederatedQueryService {

    private static final Pattern SERVICE_PATTERN = Pattern.compile(
            "SERVICE\\s+<tenant:([^>]+)>\\s*\\{([^}]+)\\}", Pattern.CASE_INSENSITIVE);

    private final EngineRegistry engineRegistry;
    private final ExecutorService executor;
    private final Semaphore concurrencySemaphore;
    private final long timeoutMs;
    private final long perSubqueryTimeoutMs;
    private final TenantAccessEvaluator tenantAccessEvaluator;
    private final MetricsService metricsService;

    public FederatedQueryService(EngineRegistry engineRegistry,
                                  FederatedQueryProperties properties,
                                  TenantAccessEvaluator tenantAccessEvaluator,
                                  MetricsService metricsService) {
        this.engineRegistry = engineRegistry;
        this.timeoutMs = properties.getTimeoutMs();
        this.perSubqueryTimeoutMs = properties.getPerSubqueryTimeoutMs() > 0
                ? properties.getPerSubqueryTimeoutMs() : properties.getTimeoutMs();
        this.concurrencySemaphore = new Semaphore(properties.getMaxConcurrency());
        this.executor = Executors.newCachedThreadPool();
        this.tenantAccessEvaluator = tenantAccessEvaluator;
        this.metricsService = metricsService;
    }

    public SparqlQueryResult executeFederated(String tenantId, String sparql) throws Exception {
        if (!containsServiceClause(sparql)) {
            OntologyEngine engine = engineRegistry.get(tenantId);
            return engine.executeQuery(sparql);
        }

        long start = System.currentTimeMillis();
        try {
            SparqlQueryResult result = doExecuteFederated(tenantId, sparql);
            long elapsed = System.currentTimeMillis() - start;
            metricsService.recordQuery(tenantId + ".federated", elapsed, true);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            metricsService.recordQuery(tenantId + ".federated", elapsed, false);
            throw e;
        }
    }

    private SparqlQueryResult doExecuteFederated(String tenantId, String sparql) throws Exception {
        Matcher matcher = SERVICE_PATTERN.matcher(sparql);

        List<String> targetIds = new ArrayList<>();
        List<String> subSparqls = new ArrayList<>();

        matcher.reset();
        while (matcher.find()) {
            targetIds.add(matcher.group(1));
            subSparqls.add(matcher.group(2).trim());
        }

        checkFederatedAccess(tenantId, targetIds);

        Map<String, String> subQueryResults = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < targetIds.size(); i++) {
            String targetId = targetIds.get(i);
            String subSparql = subSparqls.get(i);
            int index = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    concurrencySemaphore.acquire();
                    try {
                        OntologyEngine targetEngine = engineRegistry.get(targetId);
                        SparqlQueryResult subResult = targetEngine.executeQuery(subSparql);
                        String values = formatAsValues(subResult);
                        subQueryResults.put("__fed_" + index, values);
                    } finally {
                        concurrencySemaphore.release();
                    }
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, executor);

            future = future.orTimeout(perSubqueryTimeoutMs, TimeUnit.MILLISECONDS);
            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                futures.forEach(f -> f.cancel(true));
                throw new TimeoutException("Federated query timed out after " + timeoutMs + "ms");
            }
            throw e;
        }

        int idx = 0;
        matcher.reset();
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String values = subQueryResults.get("__fed_" + idx);
            matcher.appendReplacement(sb, Matcher.quoteReplacement("VALUES " + values + " "));
            idx++;
        }
        matcher.appendTail(sb);
        String modifiedSparql = sb.toString();

        OntologyEngine sourceEngine = engineRegistry.get(tenantId);
        return sourceEngine.executeQuery(modifiedSparql);
    }

    private void checkFederatedAccess(String sourceTenantId, List<String> targetIds) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            throw new SecurityException("Authentication required for federated queries");
        }

        if (!tenantAccessEvaluator.hasAccess(auth, sourceTenantId)) {
            throw new SecurityException("Access denied to source tenant: " + sourceTenantId);
        }

        for (String targetId : targetIds) {
            if (!tenantAccessEvaluator.hasAccess(auth, targetId)) {
                throw new SecurityException("Access denied to federated tenant: " + targetId);
            }
        }
    }

    public boolean containsServiceClause(String sparql) {
        return SERVICE_PATTERN.matcher(sparql).find();
    }

    private String formatAsValues(SparqlQueryResult result) {
        if (result.getVariables().isEmpty() || result.getResults().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < result.getVariables().size(); i++) {
            if (i > 0) sb.append(" ");
            sb.append("?").append(result.getVariables().get(i));
        }
        sb.append(") { ");
        for (Map<String, Object> row : result.getResults()) {
            sb.append("(");
            int vi = 0;
            for (String variable : result.getVariables()) {
                if (vi > 0) sb.append(" ");
                Object val = row.get(variable);
                if (val != null) {
                    sb.append("\"").append(val.toString().replace("\"", "\\\"")).append("\"");
                } else {
                    sb.append("UNDEF");
                }
                vi++;
            }
            sb.append(") ");
        }
        sb.append("}");
        return sb.toString();
    }
}
