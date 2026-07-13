package org.zhengyan.ontology.platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zhengyan.ontology.platform.model.SavedQuery;
import org.zhengyan.ontology.platform.repository.SavedQueryRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SavedQueryController {

    private static final String KEY_ERROR = "error";
    private static final String KEY_MESSAGE = "message";

    private final SavedQueryRepository repository;

    public SavedQueryController(SavedQueryRepository repository) {
        this.repository = repository;
    }

    @PostMapping(value = "/saved-queries", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> saveQuery(@RequestBody SaveQueryRequest request) {
        if (request.getSparql() == null || request.getSparql().isBlank()) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put(KEY_ERROR, "SPARQL_REQUIRED");
            err.put(KEY_MESSAGE, "SPARQL query is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }
        if (request.getTenantId() == null || request.getTenantId().isBlank()) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put(KEY_ERROR, "TENANT_REQUIRED");
            err.put(KEY_MESSAGE, "Tenant ID is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        SavedQuery query = new SavedQuery();
        query.setTenantId(request.getTenantId());
        query.setQuestion(request.getQuestion());
        query.setSparql(request.getSparql());
        query.setResultSummary(request.getResultSummary());
        query.setShareToken(UUID.randomUUID().toString());
        query.setCreatedAt(LocalDateTime.now());
        query.setUpdatedAt(LocalDateTime.now());

        long id = repository.save(query);
        query.setId(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("shareToken", query.getShareToken());
        result.put("shareUrl", "/api/v1/saved-queries/" + query.getShareToken());
        result.put("tenantId", query.getTenantId());
        result.put("createdAt", query.getCreatedAt().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/saved-queries/{shareToken}")
    public ResponseEntity<?> getByShareToken(@PathVariable String shareToken) {
        return repository.findByShareToken(shareToken)
                .map(query -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", query.getId());
                    result.put("tenantId", query.getTenantId());
                    result.put("question", query.getQuestion());
                    result.put("sparql", query.getSparql());
                    result.put("resultSummary", query.getResultSummary());
                    result.put("shareToken", query.getShareToken());
                    result.put("createdAt", query.getCreatedAt() != null ? query.getCreatedAt().toString() : null);
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(KEY_ERROR, "QUERY_NOT_FOUND", KEY_MESSAGE, "Saved query not found")));
    }

    @GetMapping("/tenants/{tenantId}/saved-queries")
    public ResponseEntity<?> listByTenant(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<Map<String, Object>> queries = repository.findByTenantIdPaginated(tenantId, limit, offset).stream()
                .map(q -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", q.getId());
                    m.put("tenantId", q.getTenantId());
                    m.put("question", q.getQuestion());
                    m.put("sparql", q.getSparql());
                    m.put("resultSummary", q.getResultSummary());
                    m.put("shareToken", q.getShareToken());
                    m.put("createdAt", q.getCreatedAt() != null ? q.getCreatedAt().toString() : null);
                    return m;
                }).toList();
        int total = repository.countByTenantId(tenantId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("queries", queries);
        response.put("total", total);
        response.put("limit", limit);
        response.put("offset", offset);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/saved-queries/{id}")
    public ResponseEntity<?> deleteQuery(@PathVariable long id) {
        return repository.deleteById(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    public static class SaveQueryRequest {
        private String tenantId;
        private String question;
        private String sparql;
        private String resultSummary;

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
        public String getSparql() { return sparql; }
        public void setSparql(String sparql) { this.sparql = sparql; }
        public String getResultSummary() { return resultSummary; }
        public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    }
}
