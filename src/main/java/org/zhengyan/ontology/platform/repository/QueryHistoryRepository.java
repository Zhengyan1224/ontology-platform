package org.zhengyan.ontology.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.zhengyan.ontology.platform.model.QueryHistoryEntry;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class QueryHistoryRepository {

    private final JdbcTemplate jdbc;

    public QueryHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public QueryHistoryEntry save(QueryHistoryEntry entry) {
        String sql = "INSERT INTO query_history (tenant_id, api_key_id, sparql, execution_time_ms, created_at) VALUES (?, ?, ?, ?, ?)";
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, entry.getTenantId());
            if (entry.getApiKeyId() != null) {
                ps.setLong(2, entry.getApiKeyId());
            } else {
                ps.setNull(2, java.sql.Types.BIGINT);
            }
            ps.setString(3, entry.getSparql());
            ps.setLong(4, entry.getExecutionTimeMs());
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        entry.setId(keyHolder.getKey().longValue());
        return entry;
    }

    public List<QueryHistoryEntry> findByTenant(String tenantId, int limit, int offset) {
        return jdbc.query(
                "SELECT id, tenant_id, api_key_id, sparql, execution_time_ms, created_at FROM query_history WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    QueryHistoryEntry e = new QueryHistoryEntry();
                    e.setId(rs.getLong("id"));
                    e.setTenantId(rs.getString("tenant_id"));
                    e.setApiKeyId(rs.getLong("api_key_id"));
                    if (rs.wasNull()) e.setApiKeyId(null);
                    e.setSparql(rs.getString("sparql"));
                    e.setExecutionTimeMs(rs.getLong("execution_time_ms"));
                    e.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    return e;
                }, tenantId, limit, offset);
    }

    public boolean deleteById(Long id) {
        return jdbc.update("DELETE FROM query_history WHERE id = ?", id) > 0;
    }

    public int deleteOlderThan(LocalDateTime cutoff) {
        return jdbc.update("DELETE FROM query_history WHERE created_at < ?", Timestamp.valueOf(cutoff));
    }
}
