package org.zhengyan.ontology.platform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.zhengyan.ontology.platform.model.QueryHistoryEntry;
import org.zhengyan.ontology.platform.repository.QueryHistoryRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class QueryHistoryController {

    private final QueryHistoryRepository queryHistoryRepository;

    public QueryHistoryController(QueryHistoryRepository queryHistoryRepository) {
        this.queryHistoryRepository = queryHistoryRepository;
    }

    @GetMapping("/tenants/{tenantId}/query-history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<QueryHistoryEntry>> listQueryHistory(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<QueryHistoryEntry> entries = queryHistoryRepository.findByTenant(tenantId, limit, offset);
        return ResponseEntity.ok(entries);
    }

    @DeleteMapping("/query-history/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteQueryHistory(@PathVariable Long id) {
        return queryHistoryRepository.deleteById(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
