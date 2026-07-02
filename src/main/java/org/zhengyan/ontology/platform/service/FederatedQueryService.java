package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.zhengyan.ontology.platform.model.Tenant;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FederatedQueryService {

    private static final Logger log = LoggerFactory.getLogger(FederatedQueryService.class);
    private static final Pattern SERVICE_PATTERN = Pattern.compile(
            "SERVICE\\s+<tenant:([^>]+)>\\s*\\{([^}]+)\\}", Pattern.CASE_INSENSITIVE);

    private final EngineRegistry engineRegistry;
    private final ExecutorService executor;

    public FederatedQueryService(EngineRegistry engineRegistry) {
        this.engineRegistry = engineRegistry;
        this.executor = Executors.newFixedThreadPool(4);
    }

    public SparqlQueryResult executeFederated(String tenantId, String sparql) throws Exception {
        Matcher matcher = SERVICE_PATTERN.matcher(sparql);
        if (!matcher.find()) {
            OntologyEngine engine = engineRegistry.get(tenantId);
            return engine.executeQuery(sparql);
        }

        Map<String, String> subQueryResults = new LinkedHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<String> targetIds = new ArrayList<>();
        List<String> subSparqls = new ArrayList<>();

        matcher.reset();
        while (matcher.find()) {
            String targetTenantId = matcher.group(1);
            String subSparql = matcher.group(2).trim();
            targetIds.add(targetTenantId);
            subSparqls.add(subSparql);
        }

        for (int i = 0; i < targetIds.size(); i++) {
            String targetId = targetIds.get(i);
            String subSparql = subSparqls.get(i);
            int index = i;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    OntologyEngine targetEngine = engineRegistry.get(targetId);
                    SparqlQueryResult subResult = targetEngine.executeQuery(subSparql);
                    String values = formatAsValues(subResult);
                    synchronized (subQueryResults) {
                        subQueryResults.put("__fed_" + index, values);
                    }
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        String modifiedSparql = sparql;
        int idx = 0;
        matcher.reset();
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String values = subQueryResults.get("__fed_" + idx);
            matcher.appendReplacement(sb, Matcher.quoteReplacement("VALUES " + values + " "));
            idx++;
        }
        matcher.appendTail(sb);
        modifiedSparql = sb.toString();

        OntologyEngine sourceEngine = engineRegistry.get(tenantId);
        return sourceEngine.executeQuery(modifiedSparql);
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
            for (String var : result.getVariables()) {
                if (vi > 0) sb.append(" ");
                Object val = row.get(var);
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
