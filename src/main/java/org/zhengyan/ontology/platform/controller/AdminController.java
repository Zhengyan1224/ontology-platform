package org.zhengyan.ontology.platform.controller;

import jakarta.validation.Valid;
import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.model.CreateTenantRequest;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.service.AuditService;
import org.zhengyan.ontology.platform.service.DynamicSchemaProvider;
import org.zhengyan.ontology.platform.service.MetricsService;
import org.zhengyan.ontology.platform.service.OntologySchemaProvider;
import org.zhengyan.ontology.platform.service.QueryAuditLog;
import org.zhengyan.ontology.platform.service.TenantConfigValidator;
import org.zhengyan.ontology.platform.service.TenantPersistenceService;
import org.springframework.http.HttpStatus;
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
    private final DynamicSchemaProvider dynamicSchemaProvider;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final TenantPersistenceService tenantPersistenceService;
    private final TenantConfigValidator tenantConfigValidator;

    public AdminController(TenantConfig tenantConfig,
                           EngineRegistry engineRegistry,
                           OntologySchemaProvider schemaProvider,
                           DynamicSchemaProvider dynamicSchemaProvider,
                           AuditService auditService,
                           MetricsService metricsService,
                           TenantPersistenceService tenantPersistenceService,
                           TenantConfigValidator tenantConfigValidator) {
        this.tenantConfig = tenantConfig;
        this.engineRegistry = engineRegistry;
        this.schemaProvider = schemaProvider;
        this.dynamicSchemaProvider = dynamicSchemaProvider;
        this.auditService = auditService;
        this.metricsService = metricsService;
        this.tenantPersistenceService = tenantPersistenceService;
        this.tenantConfigValidator = tenantConfigValidator;
    }

    @GetMapping("/tenants")
    public ResponseEntity<List<Map<String, Object>>> listTenants() {
        List<Map<String, Object>> tenants = getAllTenants().stream()
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

    @PostMapping("/tenants")
    public ResponseEntity<?> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        if (engineRegistry.contains(request.getId())) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", "TENANT_ALREADY_EXISTS");
            err.put("message", "Tenant already exists: " + request.getId());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(err);
        }

        Tenant tenant = new Tenant();
        tenant.setId(request.getId());
        tenant.setName(request.getName());
        tenant.setJdbcUrl(request.getJdbcUrl());
        tenant.setJdbcDriver(request.getJdbcDriver());
        tenant.setJdbcUsername(request.getJdbcUsername());
        tenant.setJdbcPassword(request.getJdbcPassword());
        tenant.setOwlPath(request.getOwlPath());
        tenant.setObdaPath(request.getObdaPath());

        Map<String, String> validationErrors = tenantConfigValidator.validate(tenant);
        if (!validationErrors.isEmpty()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "VALIDATION_ERROR");
            err.put("message", "Tenant configuration is invalid");
            err.put("details", validationErrors);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(err);
        }

        tenantPersistenceService.save(tenant);
        engineRegistry.getOrCreate(tenant);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", tenant.getId());
        result.put("name", tenant.getName());
        result.put("status", "created");
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/tenants/{tenantId}")
    public ResponseEntity<?> updateTenant(@PathVariable String tenantId,
                                          @Valid @RequestBody CreateTenantRequest request) {
        Tenant existing = findTenant(tenantId);
        if (existing == null) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", "TENANT_NOT_FOUND");
            err.put("message", "Tenant not found: " + tenantId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
        }

        existing.setName(request.getName());
        existing.setJdbcUrl(request.getJdbcUrl());
        existing.setJdbcDriver(request.getJdbcDriver());
        existing.setJdbcUsername(request.getJdbcUsername());
        existing.setJdbcPassword(request.getJdbcPassword());
        existing.setOwlPath(request.getOwlPath());
        existing.setObdaPath(request.getObdaPath());

        Map<String, String> validationErrors = tenantConfigValidator.validate(existing);
        if (!validationErrors.isEmpty()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "VALIDATION_ERROR");
            err.put("message", "Tenant configuration is invalid");
            err.put("details", validationErrors);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(err);
        }

        tenantPersistenceService.update(existing);
        engineRegistry.reinitialize(tenantId);
        engineRegistry.getOrCreate(existing);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", existing.getId());
        result.put("name", existing.getName());
        result.put("status", "updated");
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/tenants/{tenantId}")
    public ResponseEntity<?> deleteTenant(@PathVariable String tenantId) {
        Tenant existing = findTenant(tenantId);
        if (existing == null) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", "TENANT_NOT_FOUND");
            err.put("message", "Tenant not found: " + tenantId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
        }

        engineRegistry.remove(tenantId);
        tenantPersistenceService.deleteById(tenantId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tenants/{tenantId}/schema")
    public ResponseEntity<Map<String, Object>> schema(@PathVariable String tenantId) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", "TENANT_NOT_FOUND");
            err.put("message", "Tenant not found: " + tenantId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.unmodifiableMap(err));
        }

        dynamicSchemaProvider.loadFromPaths(tenant.resolveOwlPath(), tenant.resolveObdaPath());

        Map<String, Object> result = new LinkedHashMap<>(dynamicSchemaProvider.getAll());
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
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String queryType,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<QueryAuditLog> logs = auditService.getLogs(tenantId, queryType, limit, offset);
        return ResponseEntity.ok(logs.stream().map(this::logToMap).toList());
    }

    @PostMapping("/audit-log/clear")
    public ResponseEntity<Map<String, Object>> clearAuditLog() {
        auditService.clearLogs();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "cleared");
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

    private List<Tenant> getAllTenants() {
        List<Tenant> all = new ArrayList<>(tenantConfig.getTenants());
        for (Tenant persisted : tenantPersistenceService.findAll()) {
            if (all.stream().noneMatch(t -> t.getId().equals(persisted.getId()))) {
                all.add(persisted);
            }
        }
        return all;
    }

    private Tenant findTenant(String tenantId) {
        return getAllTenants().stream()
                .filter(t -> t.getId().equals(tenantId))
                .findFirst()
                .orElse(null);
    }
}
