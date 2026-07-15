package org.zhengyan.ontology.platform.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.zhengyan.ontology.platform.model.Action;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class ActionRepository {

    private static final Logger log = LoggerFactory.getLogger(ActionRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public ActionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Action> findByTenantId(String tenantId) {
        return jdbcTemplate.query(
                "SELECT * FROM actions WHERE tenant_id = ? ORDER BY created_at DESC",
                this::mapRow, tenantId);
    }

    public Action findById(String id) {
        var results = jdbcTemplate.query(
                "SELECT * FROM actions WHERE id = ?", this::mapRow, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public void save(Action action) {
        if (action.getId() == null) {
            action.setId(UUID.randomUUID().toString());
            action.setCreatedAt(LocalDateTime.now());
            jdbcTemplate.update(
                    "INSERT INTO actions (id, tenant_id, name, type, config_json, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    action.getId(), action.getTenantId(), action.getName(),
                    action.getType(), action.getConfigJson(), action.getCreatedAt());
            log.info("Created action [{}] for tenant [{}]", action.getId(), action.getTenantId());
        } else {
            jdbcTemplate.update(
                    "UPDATE actions SET name = ?, type = ?, config_json = ? WHERE id = ?",
                    action.getName(), action.getType(), action.getConfigJson(), action.getId());
            log.info("Updated action [{}] for tenant [{}]", action.getId(), action.getTenantId());
        }
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM actions WHERE id = ?", id);
        log.info("Deleted action [{}]", id);
    }

    private Action mapRow(ResultSet rs, int rowNum) throws SQLException {
        Action action = new Action();
        action.setId(rs.getString("id"));
        action.setTenantId(rs.getString("tenant_id"));
        action.setName(rs.getString("name"));
        action.setType(rs.getString("type"));
        action.setConfigJson(rs.getString("config_json"));
        if (rs.getTimestamp("created_at") != null)
            action.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return action;
    }
}
