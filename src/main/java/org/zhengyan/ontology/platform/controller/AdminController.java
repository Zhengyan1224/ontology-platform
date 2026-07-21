package org.zhengyan.ontology.platform.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.model.CreateTenantRequest;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.service.ApiKeyService;
import org.zhengyan.ontology.platform.service.CachedSparqlService;
import org.zhengyan.ontology.platform.service.DynamicSchemaProvider;
import org.zhengyan.ontology.platform.service.OntologySchemaProvider;
import org.zhengyan.ontology.platform.service.OntologyGraphService;
import org.zhengyan.ontology.platform.service.TenantConfigValidator;
import org.zhengyan.ontology.platform.service.TenantPersistenceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private static final String KEY_ERROR = "error";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_STATUS = "status";
    private static final String KEY_TENANT_ID = "tenantId";
    private static final String TENANT_NOT_FOUND = "TENANT_NOT_FOUND";
    private static final String TENANT_NOT_FOUND_PREFIX = "Tenant not found: ";

    private final TenantConfig tenantConfig;
    private final EngineRegistry engineRegistry;
    private final OntologySchemaProvider schemaProvider;
    private final DynamicSchemaProvider dynamicSchemaProvider;
    private final ApiKeyService apiKeyService;
    private final CachedSparqlService cachedSparqlService;
    private final OntologyGraphService ontologyGraphService;
    private final TenantPersistenceService tenantPersistenceService;
    private final TenantConfigValidator tenantConfigValidator;

    public AdminController(TenantConfig tenantConfig,
                           EngineRegistry engineRegistry,
                           OntologySchemaProvider schemaProvider,
                           DynamicSchemaProvider dynamicSchemaProvider,
                           ApiKeyService apiKeyService,
                           CachedSparqlService cachedSparqlService,
                           OntologyGraphService ontologyGraphService,
                           TenantPersistenceService tenantPersistenceService,
                           TenantConfigValidator tenantConfigValidator) {
        this.tenantConfig = tenantConfig;
        this.engineRegistry = engineRegistry;
        this.schemaProvider = schemaProvider;
        this.dynamicSchemaProvider = dynamicSchemaProvider;
        this.apiKeyService = apiKeyService;
        this.cachedSparqlService = cachedSparqlService;
        this.ontologyGraphService = ontologyGraphService;
        this.tenantPersistenceService = tenantPersistenceService;
        this.tenantConfigValidator = tenantConfigValidator;
    }

    @GetMapping("/tenants/{tenantId}")
    public ResponseEntity<?> getTenant(@PathVariable String tenantId) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(KEY_ERROR, TENANT_NOT_FOUND));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", tenant.getId());
        m.put("name", tenant.getName());
        m.put("jdbcUrl", tenant.getJdbcUrl());
        m.put("jdbcDriver", tenant.getJdbcDriver());
        m.put("jdbcUsername", tenant.getJdbcUsername());
        m.put("owlPath", tenant.getOwlPath());
        m.put("obdaPath", tenant.getObdaPath());
        m.put("owlContent", tenant.getOwlContent());
        m.put("obdaContent", tenant.getObdaContent());
        m.put("health", engineRegistry.isHealthy(tenantId) ? "UP" : "not_initialized");
        try {
            String desc = schemaProvider.getSchemaForTenant(tenantId);
            m.put("templateCount", desc != null ? 1 : 0);
        } catch (Exception e) {
            log.warn("Failed to get schema description for tenant [{}]: {}", tenantId, e.getMessage());
            m.put("templateCount", 0);
        }
        return ResponseEntity.ok(m);
    }

    @GetMapping("/tenants")
    public ResponseEntity<List<Map<String, Object>>> listTenants() {
        List<Map<String, Object>> tenants = getAllTenants().stream()
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("name", t.getName());
                    m.put("health", engineRegistry.isHealthy(t.getId()) ? "UP" : "not_initialized");
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(tenants);
    }

    @PostMapping("/tenants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        if (engineRegistry.contains(request.getId())) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put(KEY_ERROR, "TENANT_ALREADY_EXISTS");
            err.put(KEY_MESSAGE, "Tenant already exists: " + request.getId());
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
            err.put(KEY_ERROR, "VALIDATION_ERROR");
            err.put(KEY_MESSAGE, "Tenant configuration is invalid");
            err.put("details", validationErrors);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(err);
        }

        tenantPersistenceService.save(tenant);
        try {
            engineRegistry.getOrCreate(tenant);
        } catch (Exception e) {
            log.warn("Engine initialization deferred for tenant [{}]: {}", tenant.getId(), e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", tenant.getId());
        result.put("name", tenant.getName());
        result.put(KEY_STATUS, "created");
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/tenants/{tenantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateTenant(@PathVariable String tenantId,
                                          @Valid @RequestBody CreateTenantRequest request) {
        Tenant existing = findTenant(tenantId);
        if (existing == null) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put(KEY_ERROR, TENANT_NOT_FOUND);
            err.put(KEY_MESSAGE, TENANT_NOT_FOUND_PREFIX + tenantId);
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
            err.put(KEY_ERROR, "VALIDATION_ERROR");
            err.put(KEY_MESSAGE, "Tenant configuration is invalid");
            err.put("details", validationErrors);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(err);
        }

        tenantPersistenceService.update(existing);
        engineRegistry.remove(tenantId);
        try {
            engineRegistry.getOrCreate(existing);
        } catch (Exception e) {
            log.warn("Engine reinitialization deferred for tenant [{}] after update: {}", tenantId, e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", existing.getId());
        result.put("name", existing.getName());
        result.put(KEY_STATUS, "updated");
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/tenants/{tenantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteTenant(@PathVariable String tenantId) {
        Tenant existing = findTenant(tenantId);
        if (existing == null) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put(KEY_ERROR, TENANT_NOT_FOUND);
            err.put(KEY_MESSAGE, TENANT_NOT_FOUND_PREFIX + tenantId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
        }

        engineRegistry.remove(tenantId);
        tenantPersistenceService.deleteById(tenantId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tenants/{tenantId}/graph")
    public ResponseEntity<?> graph(@PathVariable String tenantId) {
        try {
            Map<String, Object> result = ontologyGraphService.getGraph(tenantId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(KEY_ERROR, TENANT_NOT_FOUND));
        }
    }

    @GetMapping("/tenants/{tenantId}/schema")
    public ResponseEntity<Map<String, Object>> schema(@PathVariable String tenantId) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put(KEY_ERROR, TENANT_NOT_FOUND);
            err.put(KEY_MESSAGE, TENANT_NOT_FOUND_PREFIX + tenantId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.unmodifiableMap(err));
        }

        dynamicSchemaProvider.loadFromPaths(tenant.resolveOwlPath(), tenant.resolveObdaPath());

        Map<String, Object> result = new LinkedHashMap<>(dynamicSchemaProvider.getAll());
        result.put(KEY_TENANT_ID, tenantId);
        result.put("description", schemaProvider.getSchemaForTenant(tenantId));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/tenants/{tenantId}/reinit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> reinitialize(@PathVariable String tenantId) {
        try {
            engineRegistry.reinitialize(tenantId);
            cachedSparqlService.evictForTenant(tenantId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(KEY_TENANT_ID, tenantId);
            result.put(KEY_STATUS, "reinitialized");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Engine reinitialization failed for tenant [{}]: {}", tenantId, e.getMessage());
            Map<String, Object> err = new LinkedHashMap<>();
            err.put(KEY_ERROR, "REINIT_FAILED");
            err.put(KEY_MESSAGE, "Engine reinitialization failed: " + e.getMessage());
            err.put(KEY_TENANT_ID, tenantId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    @PostMapping("/cache/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> clearCache() {
        cachedSparqlService.evictAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(KEY_STATUS, "cache_cleared");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api-keys")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> listApiKeys() {
        List<Map<String, Object>> keys = apiKeyService.listKeys().stream().map(k -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", k.getId());
            m.put("keyPrefix", k.getKeyPrefix());
            m.put("name", k.getName());
            m.put("role", k.getRole());
            m.put("enabled", k.isEnabled());
            m.put("tenantScopes", k.getTenantScopes());
            m.put("createdAt", k.getCreatedAt());
            m.put("lastUsedAt", k.getLastUsedAt());
            m.put("expiresAt", k.getExpiresAt());
            return m;
        }).toList();
        return ResponseEntity.ok(keys);
    }

    @PostMapping("/api-keys")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createApiKey(
            @Valid @RequestBody ApiKeyRequest request) {
        String rawKey = apiKeyService.generateKey(
                request.getName(), request.getRole(), request.getTenantScopes(), request.getExpiresAt());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", rawKey);
        result.put("name", request.getName());
        result.put("role", request.getRole());
        result.put("tenantScopes", request.getTenantScopes() != null ? request.getTenantScopes() : "*");
        result.put(KEY_MESSAGE, "Save this key — it will not be shown again");
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/api-keys/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> toggleApiKey(@PathVariable Long id,
                                                            @RequestParam boolean enabled) {
        boolean updated = apiKeyService.toggleKey(id, enabled);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("enabled", enabled);
        result.put(KEY_STATUS, updated ? "updated" : "not_found");
        return updated ? ResponseEntity.ok(result)
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    @PostMapping("/api-keys/{id}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> revokeApiKey(@PathVariable Long id) {
        boolean toggled = apiKeyService.toggleKey(id, false);
        apiKeyService.invalidateCache();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put(KEY_STATUS, toggled ? "revoked" : "not_found");
        return toggled ? ResponseEntity.ok(result)
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    @DeleteMapping("/api-keys/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteApiKey(@PathVariable Long id) {
        return apiKeyService.deleteKey(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
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

    public static class ApiKeyRequest {
        private String name;
        private String role = "ROLE_READONLY";
        private String tenantScopes;
        private LocalDateTime expiresAt;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getTenantScopes() { return tenantScopes; }
        public void setTenantScopes(String tenantScopes) { this.tenantScopes = tenantScopes; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    }
}
