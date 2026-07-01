package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final CopyOnWriteArrayList<QueryAuditLog> auditLogs = new CopyOnWriteArrayList<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    public void recordSparqlQuery(String tenantId, String sparql, String translatedSql,
                                  long durationMs, boolean success, String errorMessage,
                                  int resultCount) {
        QueryAuditLog entry = new QueryAuditLog(
                idSeq.getAndIncrement(), tenantId, "SPARQL", sparql, null,
                translatedSql, durationMs, success, errorMessage, resultCount);
        auditLogs.add(entry);
        log.debug("AUDIT [{}] SPARQL {} in {}ms", tenantId,
                success ? "OK" : "FAIL", durationMs);
    }

    public void recordNlqQuery(String tenantId, String question, String generatedSparql,
                               long durationMs, boolean success,
                               String errorMessage, int resultCount) {
        QueryAuditLog entry = new QueryAuditLog(
                idSeq.getAndIncrement(), tenantId, "NLQ", question, generatedSparql,
                null, durationMs, success, errorMessage, resultCount);
        auditLogs.add(entry);
        log.info("AUDIT [{}] NLQ {} in {}ms: '{}'",
                tenantId, success ? "OK" : "FAIL", durationMs, question);
    }

    public void recordNlqQuery(String tenantId, String question, String generatedSparql,
                               String translatedSql, long durationMs, boolean success,
                               String errorMessage, int resultCount) {
        QueryAuditLog entry = new QueryAuditLog(
                idSeq.getAndIncrement(), tenantId, "NLQ", question, generatedSparql,
                translatedSql, durationMs, success, errorMessage, resultCount);
        auditLogs.add(entry);
        log.info("AUDIT [{}] NLQ {} in {}ms: '{}'",
                tenantId, success ? "OK" : "FAIL", durationMs, question);
    }

    public List<QueryAuditLog> getLogs(String tenantId, String queryType,
                                        int limit, int offset) {
        return auditLogs.stream()
                .filter(l -> tenantId == null || l.getTenantId().equals(tenantId))
                .filter(l -> queryType == null || l.getQueryType().equalsIgnoreCase(queryType))
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<QueryAuditLog> getRecentLogs(int limit) {
        return getLogs(null, null, limit, 0);
    }

    public long getTotalCount() {
        return auditLogs.size();
    }

    public void clearLogs() {
        auditLogs.clear();
        log.info("Audit logs cleared");
    }
}
