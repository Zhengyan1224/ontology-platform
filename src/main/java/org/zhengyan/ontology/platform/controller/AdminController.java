package org.zhengyan.ontology.platform.controller;

import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.service.AuditService;
import org.zhengyan.ontology.platform.service.MetricsService;
import org.zhengyan.ontology.platform.service.OntologySchemaProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class AdminController {

    private final TenantConfig tenantConfig;
    private final EngineRegistry engineRegistry;
    private final OntologySchemaProvider schemaProvider;
    private final AuditService auditService;
    private final MetricsService metricsService;

    public AdminController(TenantConfig tenantConfig,
                           EngineRegistry engineRegistry,
                           OntologySchemaProvider schemaProvider,
                           AuditService auditService,
                           MetricsService metricsService) {
        this.tenantConfig = tenantConfig;
        this.engineRegistry = engineRegistry;
        this.schemaProvider = schemaProvider;
        this.auditService = auditService;
        this.metricsService = metricsService;
    }

    @GetMapping("/tenants")
    public ResponseEntity<List<Map<String, Object>>> listTenants() {
        List<Map<String, Object>> tenants = tenantConfig.getTenants().stream()
                .map(t -> {
                    OntologyEngine engine = engineRegistry.get(t.getId());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("name", t.getName());
                    m.put("health", engine != null ? engine.checkHealth() : "not_initialized");
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(tenants);
    }

    @GetMapping("/tenants/{tenantId}/schema")
    public ResponseEntity<Map<String, Object>> schema(@PathVariable String tenantId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", tenantId);
        result.put("description", schemaProvider.getSchemaForTenant(tenantId));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/tenants/{tenantId}/reinit")
    public ResponseEntity<Map<String, Object>> reinitialize(@PathVariable String tenantId) {
        engineRegistry.reinitialize(tenantId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", tenantId);
        result.put("status", "reinitialized");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/audit-log")
    public ResponseEntity<List<Map<String, Object>>> auditLog(
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(auditService.getRecentLogs(limit));
    }

    @PostMapping("/audit-log/clear")
    public ResponseEntity<Map<String, Object>> clearAuditLog() {
        auditService.clearLogs();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "cleared");
        return ResponseEntity.ok(result);
    }
}
