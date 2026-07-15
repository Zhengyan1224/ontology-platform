package org.zhengyan.ontology.platform.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.zhengyan.ontology.platform.model.RuleHistory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class RuleHistoryRepository {

    private static final Logger log = LoggerFactory.getLogger(RuleHistoryRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public RuleHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RuleHistory> findByRuleId(String ruleId) {
        return jdbcTemplate.query(
                "SELECT * FROM rule_history WHERE rule_id = ? ORDER BY evaluated_at DESC",
                this::mapRow, ruleId);
    }

    public void save(RuleHistory history) {
        if (history.getId() == null) {
            history.setId(UUID.randomUUID().toString());
        }
        if (history.getEvaluatedAt() == null) {
            history.setEvaluatedAt(LocalDateTime.now());
        }
        jdbcTemplate.update(
                "INSERT INTO rule_history (id, rule_id, tenant_id, context_json, passed, trace_json, evaluated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                history.getId(), history.getRuleId(), history.getTenantId(),
                history.getContextJson(), history.isPassed(), history.getTraceJson(),
                history.getEvaluatedAt());
        log.debug("Recorded rule evaluation history [{}]", history.getId());
    }

    public void deleteByRuleId(String ruleId) {
        jdbcTemplate.update("DELETE FROM rule_history WHERE rule_id = ?", ruleId);
    }

    private RuleHistory mapRow(ResultSet rs, int rowNum) throws SQLException {
        RuleHistory history = new RuleHistory();
        history.setId(rs.getString("id"));
        history.setRuleId(rs.getString("rule_id"));
        history.setTenantId(rs.getString("tenant_id"));
        history.setContextJson(rs.getString("context_json"));
        history.setPassed(rs.getBoolean("passed"));
        history.setTraceJson(rs.getString("trace_json"));
        if (rs.getTimestamp("evaluated_at") != null)
            history.setEvaluatedAt(rs.getTimestamp("evaluated_at").toLocalDateTime());
        return history;
    }
}
