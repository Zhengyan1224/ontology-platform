package org.zhengyan.ontology.platform.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.zhengyan.ontology.platform.model.Rule;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class RuleRepository {

    private static final Logger log = LoggerFactory.getLogger(RuleRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public RuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Rule> findByTenantId(String tenantId) {
        return jdbcTemplate.query(
                "SELECT * FROM rules WHERE tenant_id = ? ORDER BY created_at DESC",
                this::mapRow, tenantId);
    }

    public Rule findById(String id) {
        var results = jdbcTemplate.query(
                "SELECT * FROM rules WHERE id = ?", this::mapRow, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public void save(Rule rule) {
        if (rule.getId() == null) {
            rule.setId(UUID.randomUUID().toString());
            rule.setCreatedAt(LocalDateTime.now());
            rule.setUpdatedAt(LocalDateTime.now());
            jdbcTemplate.update(
                    "INSERT INTO rules (id, tenant_id, name, description, condition_expr, action_refs, enabled, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    rule.getId(), rule.getTenantId(), rule.getName(), rule.getDescription(),
                    rule.getConditionExpr(), rule.getActionRefs(), rule.isEnabled(),
                    rule.getCreatedAt(), rule.getUpdatedAt());
            log.info("Created rule [{}] for tenant [{}]", rule.getId(), rule.getTenantId());
        } else {
            rule.setUpdatedAt(LocalDateTime.now());
            jdbcTemplate.update(
                    "UPDATE rules SET name = ?, description = ?, condition_expr = ?, action_refs = ?, enabled = ?, updated_at = ? WHERE id = ?",
                    rule.getName(), rule.getDescription(), rule.getConditionExpr(),
                    rule.getActionRefs(), rule.isEnabled(), rule.getUpdatedAt(), rule.getId());
            log.info("Updated rule [{}] for tenant [{}]", rule.getId(), rule.getTenantId());
        }
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM rules WHERE id = ?", id);
        log.info("Deleted rule [{}]", id);
    }

    private Rule mapRow(ResultSet rs, int rowNum) throws SQLException {
        Rule rule = new Rule();
        rule.setId(rs.getString("id"));
        rule.setTenantId(rs.getString("tenant_id"));
        rule.setName(rs.getString("name"));
        rule.setDescription(rs.getString("description"));
        rule.setConditionExpr(rs.getString("condition_expr"));
        rule.setActionRefs(rs.getString("action_refs"));
        rule.setEnabled(rs.getBoolean("enabled"));
        if (rs.getTimestamp("created_at") != null)
            rule.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        if (rs.getTimestamp("updated_at") != null)
            rule.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return rule;
    }
}
