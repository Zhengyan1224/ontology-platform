package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final JdbcTemplate jdbcTemplate;
    private final int retentionDays;

    public AuditService(JdbcTemplate jdbcTemplate,
                        @Value("${ontology.audit.retention-days:90}") int retentionDays) {
        this.jdbcTemplate = jdbcTemplate;
        this.retentionDays = retentionDays;
    }

    public void recordSparqlQuery(String tenantId, String sparql, String translatedSql,
                                  long durationMs, boolean success, String errorMessage,
                                  int resultCount) {
        jdbcTemplate.update(
                "INSERT INTO audit_logs (tenant_id, query_type, query_text, generated_sparql, translated_sql, duration_ms, success, error_message, result_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                tenantId, "SPARQL", sparql, null, translatedSql, durationMs, success, errorMessage, resultCount);
        log.debug("AUDIT [{}] SPARQL {} in {}ms", tenantId,
                success ? "OK" : "FAIL", durationMs);
    }

    public void recordNlqQuery(String tenantId, String question, String generatedSparql,
                               long durationMs, boolean success,
                               String errorMessage, int resultCount) {
        jdbcTemplate.update(
                "INSERT INTO audit_logs (tenant_id, query_type, query_text, generated_sparql, translated_sql, duration_ms, success, error_message, result_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                tenantId, "NLQ", question, generatedSparql, null, durationMs, success, errorMessage, resultCount);
        log.info("AUDIT [{}] NLQ {} in {}ms: '{}'",
                tenantId, success ? "OK" : "FAIL", durationMs, question);
    }

    public void recordNlqQuery(String tenantId, String question, String generatedSparql,
                               String translatedSql, long durationMs, boolean success,
                               String errorMessage, int resultCount) {
        jdbcTemplate.update(
                "INSERT INTO audit_logs (tenant_id, query_type, query_text, generated_sparql, translated_sql, duration_ms, success, error_message, result_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                tenantId, "NLQ", question, generatedSparql, translatedSql, durationMs, success, errorMessage, resultCount);
        log.info("AUDIT [{}] NLQ {} in {}ms: '{}'",
                tenantId, success ? "OK" : "FAIL", durationMs, question);
    }

    public List<QueryAuditLog> getLogs(String tenantId, String queryType,
                                       int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_logs WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (tenantId != null) {
            sql.append(" AND tenant_id = ?");
            params.add(tenantId);
        }
        if (queryType != null) {
            sql.append(" AND UPPER(query_type) = UPPER(?)");
            params.add(queryType);
        }

        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), this::mapRow, params.toArray());
    }

    public List<QueryAuditLog> getRecentLogs(int limit) {
        return getLogs(null, null, limit, 0);
    }

    public long getTotalCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class);
        return count != null ? count : 0;
    }

    public void clearLogs() {
        jdbcTemplate.update("DELETE FROM audit_logs");
        log.info("Audit logs cleared");
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void purgeOldLogs() {
        int deleted = jdbcTemplate.update(
                "DELETE FROM audit_logs WHERE created_at < ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(retentionDays)));
        if (deleted > 0) {
            log.info("Purged {} audit log records older than {} days", deleted, retentionDays);
        }
    }

    private QueryAuditLog mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        return new QueryAuditLog(
                rs.getLong("id"),
                rs.getString("tenant_id"),
                rs.getString("query_type"),
                rs.getString("query_text"),
                rs.getString("generated_sparql"),
                rs.getString("translated_sql"),
                rs.getLong("duration_ms"),
                rs.getBoolean("success"),
                rs.getString("error_message"),
                rs.getInt("result_count"),
                ts != null ? ts.toLocalDateTime() : LocalDateTime.now()
        );
    }
}
