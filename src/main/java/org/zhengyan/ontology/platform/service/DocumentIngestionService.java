package org.zhengyan.ontology.platform.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.zhengyan.ontology.platform.model.Document;
import org.zhengyan.ontology.platform.model.DocumentChunk;
import org.zhengyan.ontology.platform.repository.DocumentChunkRepository;
import org.zhengyan.ontology.platform.repository.DocumentRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final int MAX_CHUNK_SIZE = 1000;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;
    private final Tika tika;

    public DocumentIngestionService(DocumentRepository documentRepository,
                                    DocumentChunkRepository documentChunkRepository,
                                    EmbeddingService embeddingService) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingService = embeddingService;
        this.tika = new Tika();
    }

    public Document uploadDocument(String tenantId, String name, MultipartFile file) {
        Document doc = new Document();
        doc.setTenantId(tenantId);
        doc.setName(name != null && !name.isBlank() ? name : file.getOriginalFilename());
        doc.setFileName(file.getOriginalFilename());
        doc.setContentType(file.getContentType());
        doc.setFileSize(file.getSize());
        doc.setStatus("PROCESSING");
        doc.setChunkCount(0);
        documentRepository.save(doc);

        try {
            String text = parseFile(file);
            List<String> chunks = embeddingService.chunkText(text, MAX_CHUNK_SIZE);

            List<DocumentChunk> chunkEntities = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunk chunk = new DocumentChunk();
                chunk.setDocumentId(doc.getId());
                chunk.setTenantId(tenantId);
                chunk.setChunkIndex(i);
                chunk.setContent(chunks.get(i));
                chunkEntities.add(chunk);
            }
            documentChunkRepository.saveBatch(chunkEntities);

            doc.setStatus("READY");
            doc.setChunkCount(chunks.size());
            doc.setErrorMessage(null);
        } catch (Exception e) {
            log.warn("Failed to process document [{}]: {}", doc.getId(), e.getMessage());
            doc.setStatus("ERROR");
            doc.setErrorMessage(e.getMessage());
        }
        documentRepository.save(doc);
        return doc;
    }

    public List<Document> listDocuments(String tenantId) {
        return documentRepository.findByTenantId(tenantId);
    }

    public Document getDocument(String tenantId, String documentId) {
        Document doc = documentRepository.findById(documentId);
        if (doc != null && doc.getTenantId().equals(tenantId)) {
            return doc;
        }
        return null;
    }

    public List<DocumentChunk> getChunks(String tenantId, String documentId) {
        Document doc = documentRepository.findById(documentId);
        if (doc == null || !doc.getTenantId().equals(tenantId)) {
            return List.of();
        }
        return documentChunkRepository.findByDocumentId(documentId);
    }

    public boolean deleteDocument(String tenantId, String documentId) {
        Document doc = documentRepository.findById(documentId);
        if (doc == null || !doc.getTenantId().equals(tenantId)) {
            return false;
        }
        documentChunkRepository.deleteByDocumentId(documentId);
        documentRepository.deleteById(documentId);
        return true;
    }

    public List<DocumentQueryResult> queryDocuments(String tenantId, String queryText, int topK) {
        List<DocumentChunk> allChunks = documentChunkRepository.findByTenantId(tenantId);
        if (allChunks.isEmpty()) return List.of();

        List<String> chunkTexts = allChunks.stream()
                .map(DocumentChunk::getContent)
                .collect(Collectors.toList());

        List<EmbeddingService.ScoredResult> scored = embeddingService.search(queryText, chunkTexts, topK);

        List<DocumentQueryResult> results = new ArrayList<>();
        for (EmbeddingService.ScoredResult sr : scored) {
            DocumentChunk chunk = allChunks.get(sr.index());
            results.add(new DocumentQueryResult(
                    chunk.getId(),
                    chunk.getDocumentId(),
                    chunk.getChunkIndex(),
                    chunk.getContent(),
                    sr.score()
            ));
        }
        return results;
    }

    private String parseFile(MultipartFile file) throws IOException, TikaException {
        try (InputStream is = file.getInputStream()) {
            return tika.parseToString(is);
        }
    }

    public record DocumentQueryResult(String chunkId, String documentId, int chunkIndex, String content, double score) {}
}
