package org.zhengyan.ontology.platform.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.zhengyan.ontology.platform.model.Document;
import org.zhengyan.ontology.platform.model.DocumentChunk;
import org.zhengyan.ontology.platform.service.DocumentIngestionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    private final DocumentIngestionService documentIngestionService;

    public DocumentController(DocumentIngestionService documentIngestionService) {
        this.documentIngestionService = documentIngestionService;
    }

    @GetMapping
    public ResponseEntity<List<Document>> listDocuments(@PathVariable String tenantId) {
        return ResponseEntity.ok(documentIngestionService.listDocuments(tenantId));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<Document> getDocument(@PathVariable String tenantId,
                                                @PathVariable String documentId) {
        Document doc = documentIngestionService.getDocument(tenantId, documentId);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(doc);
    }

    @PostMapping("/upload")
    public ResponseEntity<Document> uploadDocument(@PathVariable String tenantId,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "name", required = false) String name) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Document doc = documentIngestionService.uploadDocument(tenantId, name, file);
        HttpStatus status = "ERROR".equals(doc.getStatus()) ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(doc);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String tenantId,
                                               @PathVariable String documentId) {
        boolean deleted = documentIngestionService.deleteDocument(tenantId, documentId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{documentId}/chunks")
    public ResponseEntity<List<DocumentChunk>> getChunks(@PathVariable String tenantId,
                                                         @PathVariable String documentId) {
        List<DocumentChunk> chunks = documentIngestionService.getChunks(tenantId, documentId);
        return ResponseEntity.ok(chunks);
    }

    @PostMapping("/query")
    public ResponseEntity<List<DocumentIngestionService.DocumentQueryResult>> queryDocuments(
            @PathVariable String tenantId,
            @RequestBody Map<String, Object> body) {
        String queryText = body.getOrDefault("query", "").toString();
        int topK = body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 5;
        if (queryText.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(documentIngestionService.queryDocuments(tenantId, queryText, topK));
    }
}
