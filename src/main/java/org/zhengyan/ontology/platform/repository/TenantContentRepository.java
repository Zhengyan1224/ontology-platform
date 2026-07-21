package org.zhengyan.ontology.platform.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
public class TenantContentRepository {

    private static final Logger log = LoggerFactory.getLogger(TenantContentRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public TenantContentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TenantContent findByTenantId(String tenantId) {
        var results = jdbcTemplate.query(
                "SELECT * FROM tenant_content WHERE tenant_id = ?",
                this::mapRow, tenantId);
        return results.isEmpty() ? null : results.get(0);
    }

    public void upsert(String tenantId, String owlContent, String obdaContent) {
        upsert(tenantId, owlContent, obdaContent, null);
    }

    public void upsert(String tenantId, String owlContent, String obdaContent, String axiomConfig) {
        jdbcTemplate.update(
                "MERGE INTO tenant_content (tenant_id, owl_content, obda_content, axiom_config, updated_at) KEY (tenant_id) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                tenantId, owlContent, obdaContent, axiomConfig);
        log.info("Saved content for tenant [{}]", tenantId);
    }

    public void updateAxiomConfig(String tenantId, String axiomConfig) {
        jdbcTemplate.update(
                "MERGE INTO tenant_content (tenant_id, owl_content, obda_content, axiom_config, updated_at) KEY (tenant_id) VALUES (?, NULL, NULL, ?, CURRENT_TIMESTAMP)",
                tenantId, axiomConfig);
        log.info("Updated axiom_config for tenant [{}]", tenantId);
    }

    public void deleteByTenantId(String tenantId) {
        jdbcTemplate.update("DELETE FROM tenant_content WHERE tenant_id = ?", tenantId);
        log.info("Deleted content for tenant [{}]", tenantId);
    }

    private TenantContent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TenantContent(
                rs.getString("tenant_id"),
                rs.getString("owl_content"),
                rs.getString("obda_content"),
                rs.getString("axiom_config"));
    }

    public record TenantContent(String tenantId, String owlContent, String obdaContent, String axiomConfig) {}
}
