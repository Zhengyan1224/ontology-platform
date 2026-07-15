package org.zhengyan.ontology.platform.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.zhengyan.ontology.platform.model.DocumentChunk;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class DocumentChunkRepository {
    private static final Logger log = LoggerFactory.getLogger(DocumentChunkRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DocumentChunk> findByDocumentId(String documentId) {
        return jdbcTemplate.query(
                "SELECT * FROM document_chunks WHERE document_id = ? ORDER BY chunk_index",
                this::mapRow, documentId);
    }

    public List<DocumentChunk> findByTenantId(String tenantId) {
        return jdbcTemplate.query(
                "SELECT * FROM document_chunks WHERE tenant_id = ? ORDER BY created_at DESC",
                this::mapRow, tenantId);
    }

    public DocumentChunk findById(String id) {
        var results = jdbcTemplate.query(
                "SELECT * FROM document_chunks WHERE id = ?", this::mapRow, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public void save(DocumentChunk chunk) {
        if (chunk.getId() == null) {
            chunk.setId(UUID.randomUUID().toString());
            chunk.setCreatedAt(LocalDateTime.now());
            jdbcTemplate.update(
                    "INSERT INTO document_chunks (id, document_id, tenant_id, chunk_index, content, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    chunk.getId(), chunk.getDocumentId(), chunk.getTenantId(),
                    chunk.getChunkIndex(), chunk.getContent(), chunk.getCreatedAt());
        }
    }

    public void saveBatch(List<DocumentChunk> chunks) {
        for (DocumentChunk chunk : chunks) {
            save(chunk);
        }
        if (!chunks.isEmpty()) {
            log.info("Saved {} chunks for document [{}]", chunks.size(), chunks.get(0).getDocumentId());
        }
    }

    public void deleteByDocumentId(String documentId) {
        jdbcTemplate.update("DELETE FROM document_chunks WHERE document_id = ?", documentId);
        log.info("Deleted chunks for document [{}]", documentId);
    }

    public void deleteByTenantId(String tenantId) {
        jdbcTemplate.update("DELETE FROM document_chunks WHERE tenant_id = ?", tenantId);
        log.info("Deleted all chunks for tenant [{}]", tenantId);
    }

    private DocumentChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(rs.getString("id"));
        chunk.setDocumentId(rs.getString("document_id"));
        chunk.setTenantId(rs.getString("tenant_id"));
        chunk.setChunkIndex(rs.getInt("chunk_index"));
        chunk.setContent(rs.getString("content"));
        if (rs.getTimestamp("created_at") != null)
            chunk.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return chunk;
    }
}
