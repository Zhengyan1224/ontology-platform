package org.zhengyan.ontology.platform.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.zhengyan.ontology.platform.model.OntologyProposal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class OntologyProposalRepository {

    private static final Logger log = LoggerFactory.getLogger(OntologyProposalRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public OntologyProposalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<OntologyProposal> findByTenantId(String tenantId) {
        return jdbcTemplate.query(
                "SELECT * FROM ontology_proposals WHERE tenant_id = ? ORDER BY created_at DESC",
                this::mapRow, tenantId);
    }

    public OntologyProposal findById(String id) {
        var results = jdbcTemplate.query(
                "SELECT * FROM ontology_proposals WHERE id = ?", this::mapRow, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public void save(OntologyProposal proposal) {
        if (proposal.getId() == null) {
            proposal.setId(UUID.randomUUID().toString());
            proposal.setCreatedAt(LocalDateTime.now());
            proposal.setUpdatedAt(LocalDateTime.now());
            jdbcTemplate.update(
                    "INSERT INTO ontology_proposals (id, tenant_id, title, description, proposed_owl, proposed_obda, status, source, rejection_reason, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    proposal.getId(), proposal.getTenantId(), proposal.getTitle(),
                    proposal.getDescription(), proposal.getProposedOwl(),
                    proposal.getProposedObda(), proposal.getStatus(),
                    proposal.getSource(), proposal.getRejectionReason(),
                    proposal.getCreatedAt(), proposal.getUpdatedAt());
            log.info("Created ontology proposal [{}] for tenant [{}]", proposal.getId(), proposal.getTenantId());
        } else {
            proposal.setUpdatedAt(LocalDateTime.now());
            jdbcTemplate.update(
                    "UPDATE ontology_proposals SET title = ?, description = ?, proposed_owl = ?, proposed_obda = ?, status = ?, source = ?, rejection_reason = ?, updated_at = ? WHERE id = ?",
                    proposal.getTitle(), proposal.getDescription(),
                    proposal.getProposedOwl(), proposal.getProposedObda(),
                    proposal.getStatus(), proposal.getSource(),
                    proposal.getRejectionReason(), proposal.getUpdatedAt(),
                    proposal.getId());
            log.info("Updated ontology proposal [{}] for tenant [{}]", proposal.getId(), proposal.getTenantId());
        }
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM ontology_proposals WHERE id = ?", id);
        log.info("Deleted ontology proposal [{}]", id);
    }

    private OntologyProposal mapRow(ResultSet rs, int rowNum) throws SQLException {
        OntologyProposal p = new OntologyProposal();
        p.setId(rs.getString("id"));
        p.setTenantId(rs.getString("tenant_id"));
        p.setTitle(rs.getString("title"));
        p.setDescription(rs.getString("description"));
        p.setProposedOwl(rs.getString("proposed_owl"));
        p.setProposedObda(rs.getString("proposed_obda"));
        p.setStatus(rs.getString("status"));
        p.setSource(rs.getString("source"));
        p.setRejectionReason(rs.getString("rejection_reason"));
        if (rs.getTimestamp("created_at") != null)
            p.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        if (rs.getTimestamp("updated_at") != null)
            p.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return p;
    }
}
