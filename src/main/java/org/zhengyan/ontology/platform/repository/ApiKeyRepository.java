package org.zhengyan.ontology.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.zhengyan.ontology.platform.model.ApiKeyEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
/**
 * @author 郑炎 Zheng Yan
 */
public class ApiKeyRepository {

    private final JdbcTemplate jdbcTemplate;

    public ApiKeyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int save(ApiKeyEntity key) {
        return jdbcTemplate.update(
                "INSERT INTO api_keys (key_hash, key_prefix, name, role, tenant_scopes, enabled, created_at, updated_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                key.getKeyHash(), key.getKeyPrefix(), key.getName(), key.getRole(),
                key.getTenantScopes() != null ? key.getTenantScopes() : "*",
                key.isEnabled(), Timestamp.valueOf(key.getCreatedAt()),
                Timestamp.valueOf(key.getUpdatedAt()),
                key.getExpiresAt() != null ? Timestamp.valueOf(key.getExpiresAt()) : null);
    }

    public Optional<ApiKeyEntity> findByKeyHash(String keyHash) {
        List<ApiKeyEntity> results = jdbcTemplate.query(
                "SELECT * FROM api_keys WHERE key_hash = ?", new ApiKeyRowMapper(), keyHash);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<ApiKeyEntity> findAll() {
        return jdbcTemplate.query("SELECT * FROM api_keys ORDER BY created_at DESC", new ApiKeyRowMapper());
    }

    public List<ApiKeyEntity> findAllEnabled() {
        return jdbcTemplate.query(
                "SELECT * FROM api_keys WHERE enabled = TRUE ORDER BY created_at DESC",
                new ApiKeyRowMapper());
    }

    public int updateLastUsedAt(Long id, LocalDateTime time) {
        return jdbcTemplate.update(
                "UPDATE api_keys SET last_used_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.valueOf(time), Timestamp.valueOf(time), id);
    }

    public int updateEnabled(Long id, boolean enabled) {
        return jdbcTemplate.update(
                "UPDATE api_keys SET enabled = ?, updated_at = ? WHERE id = ?",
                enabled, Timestamp.valueOf(LocalDateTime.now()), id);
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM api_keys WHERE id = ?", id);
    }

    public boolean existsByKeyHash(String keyHash) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_keys WHERE key_hash = ?", Integer.class, keyHash);
        return count != null && count > 0;
    }

    public long count() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM api_keys", Integer.class);
        return count != null ? count : 0;
    }

    private static class ApiKeyRowMapper implements RowMapper<ApiKeyEntity> {
        @Override
        public ApiKeyEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            Timestamp lastUsedAt = rs.getTimestamp("last_used_at");
            Timestamp expiresAt = rs.getTimestamp("expires_at");
            String tenantScopes = rs.getString("tenant_scopes");
            return new ApiKeyEntity(
                    rs.getLong("id"),
                    rs.getString("key_hash"),
                    rs.getString("key_prefix"),
                    rs.getString("name"),
                    rs.getString("role"),
                    tenantScopes != null ? tenantScopes : "*",
                    rs.getBoolean("enabled"),
                    createdAt != null ? createdAt.toLocalDateTime() : LocalDateTime.now(),
                    updatedAt != null ? updatedAt.toLocalDateTime() : LocalDateTime.now(),
                    lastUsedAt != null ? lastUsedAt.toLocalDateTime() : null,
                    expiresAt != null ? expiresAt.toLocalDateTime() : null
            );
        }
    }
}
