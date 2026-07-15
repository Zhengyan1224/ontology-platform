package org.zhengyan.ontology.platform.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.zhengyan.ontology.platform.model.Workflow;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class WorkflowRepository {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public WorkflowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Workflow> findByTenantId(String tenantId) {
        return jdbcTemplate.query(
                "SELECT * FROM workflows WHERE tenant_id = ? ORDER BY created_at DESC",
                this::mapRow, tenantId);
    }

    public Workflow findById(String id) {
        var results = jdbcTemplate.query(
                "SELECT * FROM workflows WHERE id = ?", this::mapRow, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public void save(Workflow workflow) {
        if (workflow.getId() == null) {
            workflow.setId(UUID.randomUUID().toString());
            workflow.setCreatedAt(LocalDateTime.now());
            jdbcTemplate.update(
                    "INSERT INTO workflows (id, tenant_id, name, dag_json, enabled, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    workflow.getId(), workflow.getTenantId(), workflow.getName(),
                    workflow.getDagJson(), workflow.isEnabled(), workflow.getCreatedAt());
            log.info("Created workflow [{}] for tenant [{}]", workflow.getId(), workflow.getTenantId());
        } else {
            jdbcTemplate.update(
                    "UPDATE workflows SET name = ?, dag_json = ?, enabled = ? WHERE id = ?",
                    workflow.getName(), workflow.getDagJson(), workflow.isEnabled(), workflow.getId());
            log.info("Updated workflow [{}] for tenant [{}]", workflow.getId(), workflow.getTenantId());
        }
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM workflows WHERE id = ?", id);
        log.info("Deleted workflow [{}]", id);
    }

    private Workflow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Workflow w = new Workflow();
        w.setId(rs.getString("id"));
        w.setTenantId(rs.getString("tenant_id"));
        w.setName(rs.getString("name"));
        w.setDagJson(rs.getString("dag_json"));
        w.setEnabled(rs.getBoolean("enabled"));
        if (rs.getTimestamp("created_at") != null)
            w.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return w;
    }
}
