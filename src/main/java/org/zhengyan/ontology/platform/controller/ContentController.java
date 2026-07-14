package org.zhengyan.ontology.platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.repository.TenantContentRepository;
import org.zhengyan.ontology.platform.service.CachedSparqlService;
import org.zhengyan.ontology.platform.service.ObdaGeneratorService;
import org.zhengyan.ontology.platform.service.OwlGeneratorService;
import org.zhengyan.ontology.platform.service.SqlExecutionService;
import org.zhengyan.ontology.platform.service.TenantPersistenceService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ContentController {

    private static final String KEY_ERROR = "error";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_STATUS = "status";
    private static final String TENANT_NOT_FOUND = "TENANT_NOT_FOUND";

    private final TenantConfig tenantConfig;
    private final OwlGeneratorService owlGeneratorService;
    private final ObdaGeneratorService obdaGeneratorService;
    private final TenantPersistenceService tenantPersistenceService;
    private final TenantContentRepository tenantContentRepository;
    private final EngineRegistry engineRegistry;
    private final CachedSparqlService cachedSparqlService;
    private final SqlExecutionService sqlExecutionService;

    public ContentController(TenantConfig tenantConfig,
                             OwlGeneratorService owlGeneratorService,
                             ObdaGeneratorService obdaGeneratorService,
                             TenantPersistenceService tenantPersistenceService,
                             TenantContentRepository tenantContentRepository,
                             EngineRegistry engineRegistry,
                             CachedSparqlService cachedSparqlService,
                             SqlExecutionService sqlExecutionService) {
        this.tenantConfig = tenantConfig;
        this.owlGeneratorService = owlGeneratorService;
        this.obdaGeneratorService = obdaGeneratorService;
        this.tenantPersistenceService = tenantPersistenceService;
        this.tenantContentRepository = tenantContentRepository;
        this.engineRegistry = engineRegistry;
        this.cachedSparqlService = cachedSparqlService;
        this.sqlExecutionService = sqlExecutionService;
    }

    @PostMapping("/tenants/{tenantId}/generate")
    public ResponseEntity<?> generate(@PathVariable String tenantId) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, TENANT_NOT_FOUND, KEY_MESSAGE, "Tenant not found: " + tenantId));
        }
        try {
            String owl = owlGeneratorService.generateOwl(tenant);
            String obda = obdaGeneratorService.generateObda(tenant);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("owlContent", owl);
            result.put("obdaContent", obda);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put(KEY_ERROR, "MAPPING_GENERATION_FAILED");
            err.put(KEY_MESSAGE, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    @PutMapping("/tenants/{tenantId}/content")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> saveContent(@PathVariable String tenantId,
                                         @RequestBody ContentBody body) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, TENANT_NOT_FOUND, KEY_MESSAGE, "Tenant not found: " + tenantId));
        }
        tenantContentRepository.upsert(tenantId, body.owlContent, body.obdaContent);
        return ResponseEntity.ok(Map.of(KEY_STATUS, "saved"));
    }

    @PostMapping("/tenants/{tenantId}/apply")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> applyContent(@PathVariable String tenantId,
                                          @RequestBody ContentBody body) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, TENANT_NOT_FOUND, KEY_MESSAGE, "Tenant not found: " + tenantId));
        }
        try {
            tenantContentRepository.upsert(tenantId, body.owlContent, body.obdaContent);

            Tenant updated = tenantPersistenceService.findById(tenantId);
            if (updated != null) {
                engineRegistry.reinitialize(tenantId);
                engineRegistry.getOrCreate(updated);
            }
            cachedSparqlService.evictForTenant(tenantId);

            String health = engineRegistry.isHealthy(tenantId) ? "UP" : "DOWN";
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(KEY_STATUS, "applied");
            result.put("health", health);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put(KEY_ERROR, "APPLY_FAILED");
            err.put(KEY_MESSAGE, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    @PostMapping("/tenants/{tenantId}/sql")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> executeSql(@PathVariable String tenantId,
                                        @RequestBody SqlBody body) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, TENANT_NOT_FOUND, KEY_MESSAGE, "Tenant not found: " + tenantId));
        }
        try {
            SqlExecutionService.SqlResult result = sqlExecutionService.execute(tenant, body.sql());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", result.success());
            resp.put("columns", result.columns());
            resp.put("error", result.error());
            resp.put("rowCount", result.rowCount());
            resp.put("executionTimeMs", result.executionTimeMs());
            resp.put("rows", result.rows());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put(KEY_ERROR, "SQL_EXECUTION_FAILED");
            err.put(KEY_MESSAGE, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    private Tenant findTenant(String tenantId) {
        List<Tenant> all = tenantConfig.getTenants();
        return all.stream()
                .filter(t -> t.getId().equals(tenantId))
                .findFirst()
                .orElseGet(() -> tenantPersistenceService.findById(tenantId));
    }

    public record ContentBody(String owlContent, String obdaContent) {}
    public record SqlBody(String sql) {}
}
