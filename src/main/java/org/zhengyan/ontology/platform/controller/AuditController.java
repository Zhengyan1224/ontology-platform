package org.zhengyan.ontology.platform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.zhengyan.ontology.platform.service.AuditService;
import org.zhengyan.ontology.platform.service.QueryAuditLog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuditController {

    private static final String KEY_STATUS = "status";

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/audit-log")
    public ResponseEntity<List<Map<String, Object>>> auditLog(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String queryType,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<QueryAuditLog> logs = auditService.getLogs(tenantId, queryType, limit, offset);
        return ResponseEntity.ok(logs.stream().map(this::logToMap).toList());
    }

    @PostMapping("/audit-log/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> clearAuditLog() {
        auditService.clearLogs();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(KEY_STATUS, "cleared");
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> logToMap(QueryAuditLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", log.getId());
        m.put("tenantId", log.getTenantId());
        m.put("queryType", log.getQueryType());
        m.put("queryText", log.getQueryText());
        m.put("generatedSparql", log.getGeneratedSparql());
        m.put("translatedSql", log.getTranslatedSql());
        m.put("durationMs", log.getDurationMs());
        m.put("success", log.isSuccess());
        m.put("errorMessage", log.getErrorMessage());
        m.put("resultCount", log.getResultCount());
        m.put("timestamp", log.getTimestamp());
        return m;
    }
}
