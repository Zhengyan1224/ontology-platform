package org.zhengyan.ontology.platform.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.zhengyan.ontology.platform.model.SavedQuery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * @author 郑炎 Zheng Yan
 */
@Repository
public class SavedQueryRepository {

    private static final Logger log = LoggerFactory.getLogger(SavedQueryRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public SavedQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long save(SavedQuery query) {
        String sql = "INSERT INTO saved_queries (tenant_id, question, sparql, result_summary, share_token, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, query.getTenantId(), query.getQuestion(), query.getSparql(),
                query.getResultSummary(), query.getShareToken(),
                Timestamp.valueOf(query.getCreatedAt()), Timestamp.valueOf(query.getUpdatedAt()));
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM saved_queries WHERE share_token = ?", Long.class, query.getShareToken());
        return id != null ? id : 0;
    }

    public Optional<SavedQuery> findById(long id) {
        List<SavedQuery> results = jdbcTemplate.query("SELECT * FROM saved_queries WHERE id = ?", new SavedQueryRowMapper(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<SavedQuery> findByShareToken(String shareToken) {
        List<SavedQuery> results = jdbcTemplate.query("SELECT * FROM saved_queries WHERE share_token = ?", new SavedQueryRowMapper(), shareToken);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<SavedQuery> findByTenantId(String tenantId) {
        return jdbcTemplate.query("SELECT * FROM saved_queries WHERE tenant_id = ? ORDER BY updated_at DESC", new SavedQueryRowMapper(), tenantId);
    }

    public List<SavedQuery> findByTenantIdPaginated(String tenantId, int limit, int offset) {
        return jdbcTemplate.query("SELECT * FROM saved_queries WHERE tenant_id = ? ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                new SavedQueryRowMapper(), tenantId, limit, offset);
    }

    public int countByTenantId(String tenantId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM saved_queries WHERE tenant_id = ?", Integer.class, tenantId);
        return count != null ? count : 0;
    }

    public boolean deleteById(long id) {
        int affected = jdbcTemplate.update("DELETE FROM saved_queries WHERE id = ?", id);
        return affected > 0;
    }

    private static class SavedQueryRowMapper implements RowMapper<SavedQuery> {
        @Override
        public SavedQuery mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            return new SavedQuery(
                    rs.getLong("id"),
                    rs.getString("tenant_id"),
                    rs.getString("question"),
                    rs.getString("sparql"),
                    rs.getString("result_summary"),
                    rs.getString("share_token"),
                    createdAt != null ? createdAt.toLocalDateTime() : null,
                    updatedAt != null ? updatedAt.toLocalDateTime() : null
            );
        }
    }
}
