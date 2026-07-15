package org.zhengyan.ontology.platform.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.zhengyan.ontology.platform.model.Document;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class DocumentRepository {
    private static final Logger log = LoggerFactory.getLogger(DocumentRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Document> findByTenantId(String tenantId) {
        return jdbcTemplate.query(
                "SELECT * FROM documents WHERE tenant_id = ? ORDER BY created_at DESC",
                this::mapRow, tenantId);
    }

    public Document findById(String id) {
        var results = jdbcTemplate.query(
                "SELECT * FROM documents WHERE id = ?", this::mapRow, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public void save(Document doc) {
        if (doc.getId() == null) {
            doc.setId(UUID.randomUUID().toString());
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());
            jdbcTemplate.update(
                    "INSERT INTO documents (id, tenant_id, name, file_name, content_type, file_size, status, error_message, chunk_count, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    doc.getId(), doc.getTenantId(), doc.getName(), doc.getFileName(),
                    doc.getContentType(), doc.getFileSize(), doc.getStatus(),
                    doc.getErrorMessage(), doc.getChunkCount(),
                    doc.getCreatedAt(), doc.getUpdatedAt());
            log.info("Created document [{}] for tenant [{}]", doc.getId(), doc.getTenantId());
        } else {
            doc.setUpdatedAt(LocalDateTime.now());
            jdbcTemplate.update(
                    "UPDATE documents SET name = ?, file_name = ?, content_type = ?, file_size = ?, status = ?, error_message = ?, chunk_count = ?, updated_at = ? WHERE id = ?",
                    doc.getName(), doc.getFileName(), doc.getContentType(),
                    doc.getFileSize(), doc.getStatus(), doc.getErrorMessage(),
                    doc.getChunkCount(), doc.getUpdatedAt(), doc.getId());
            log.info("Updated document [{}] for tenant [{}]", doc.getId(), doc.getTenantId());
        }
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM documents WHERE id = ?", id);
        log.info("Deleted document [{}]", id);
    }

    private Document mapRow(ResultSet rs, int rowNum) throws SQLException {
        Document doc = new Document();
        doc.setId(rs.getString("id"));
        doc.setTenantId(rs.getString("tenant_id"));
        doc.setName(rs.getString("name"));
        doc.setFileName(rs.getString("file_name"));
        doc.setContentType(rs.getString("content_type"));
        doc.setFileSize(rs.getLong("file_size"));
        doc.setStatus(rs.getString("status"));
        doc.setErrorMessage(rs.getString("error_message"));
        doc.setChunkCount(rs.getInt("chunk_count"));
        if (rs.getTimestamp("created_at") != null)
            doc.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        if (rs.getTimestamp("updated_at") != null)
            doc.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return doc;
    }
}
