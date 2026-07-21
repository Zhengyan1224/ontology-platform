package org.zhengyan.ontology.platform.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.repository.TenantContentRepository;
import org.zhengyan.ontology.platform.service.CachedSparqlService;
import org.zhengyan.ontology.platform.service.GraphLlmService;
import org.zhengyan.ontology.platform.service.ObdaGeneratorService;
import org.zhengyan.ontology.platform.service.OntologyGraphService;
import org.zhengyan.ontology.platform.service.OwlGeneratorService;
import org.zhengyan.ontology.platform.service.SqlExecutionService;
import org.zhengyan.ontology.platform.service.TenantPersistenceService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class ContentController {

    private static final Logger log = LoggerFactory.getLogger(ContentController.class);
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
    private final OntologyGraphService ontologyGraphService;
    private final GraphLlmService graphLlmService;

    public ContentController(TenantConfig tenantConfig,
                             OwlGeneratorService owlGeneratorService,
                             ObdaGeneratorService obdaGeneratorService,
                             TenantPersistenceService tenantPersistenceService,
                             TenantContentRepository tenantContentRepository,
                             EngineRegistry engineRegistry,
                             CachedSparqlService cachedSparqlService,
                             SqlExecutionService sqlExecutionService,
                             OntologyGraphService ontologyGraphService,
                             GraphLlmService graphLlmService) {
        this.tenantConfig = tenantConfig;
        this.owlGeneratorService = owlGeneratorService;
        this.obdaGeneratorService = obdaGeneratorService;
        this.tenantPersistenceService = tenantPersistenceService;
        this.tenantContentRepository = tenantContentRepository;
        this.engineRegistry = engineRegistry;
        this.cachedSparqlService = cachedSparqlService;
        this.sqlExecutionService = sqlExecutionService;
        this.ontologyGraphService = ontologyGraphService;
        this.graphLlmService = graphLlmService;
    }

    @PostMapping("/tenants/{tenantId}/generate")
    public ResponseEntity<?> generate(@PathVariable String tenantId) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, TENANT_NOT_FOUND, KEY_MESSAGE, "Tenant not found: " + tenantId));
        }
        try {
            tenantContentRepository.updateAxiomConfig(tenantId, null);
            ontologyGraphService.evictCache(tenantId);

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
        writeContentFile(tenant.resolveOwlPath(), body.owlContent);
        writeContentFile(tenant.resolveObdaPath(), body.obdaContent);
        return ResponseEntity.ok(Map.of(KEY_STATUS, "saved"));
    }

    @GetMapping("/tenants/{tenantId}/axiom-config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAxiomConfig(@PathVariable String tenantId) {
        TenantContentRepository.TenantContent content = tenantContentRepository.findByTenantId(tenantId);
        if (content == null) {
            return ResponseEntity.ok(Map.of("subClassOf", List.of(), "layout", Map.of()));
        }
        String config = content.axiomConfig();
        try {
            JsonNode node = new ObjectMapper().readTree(config != null ? config : "{}");
            return ResponseEntity.ok(new ObjectMapper().convertValue(node, Map.class));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("subClassOf", List.of(), "layout", Map.of()));
        }
    }

    @PutMapping("/tenants/{tenantId}/axiom-config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> saveAxiomConfig(@PathVariable String tenantId,
                                             @RequestBody Map<String, Object> body) {
        try {
            String error = validateAxiomConfig(body);
            if (error != null) {
                return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "VALIDATION_FAILED", KEY_MESSAGE, error));
            }
            String json = new ObjectMapper().writeValueAsString(body);
            Optional<TenantContentRepository.TenantContent> existing = Optional.ofNullable(
                    tenantContentRepository.findByTenantId(tenantId));
            String owl = existing.map(TenantContentRepository.TenantContent::owlContent).orElse(null);
            String obda = existing.map(TenantContentRepository.TenantContent::obdaContent).orElse(null);
            tenantContentRepository.upsert(tenantId, owl, obda, json);
            return ResponseEntity.ok(Map.of(KEY_STATUS, "saved"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "SAVE_FAILED", KEY_MESSAGE, e.getMessage()));
        }
    }

    @PostMapping("/tenants/{tenantId}/axiom-config/suggest")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> suggestAxioms(@PathVariable String tenantId) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, TENANT_NOT_FOUND, KEY_MESSAGE, "Tenant not found: " + tenantId));
        }
        try {
            TenantContentRepository.TenantContent content = tenantContentRepository.findByTenantId(tenantId);
            String config = content != null ? content.axiomConfig() : null;
            Map<String, Object> axiomConfig = new LinkedHashMap<>();
            if (config != null) {
                try {
                    JsonNode node = new ObjectMapper().readTree(config);
                    axiomConfig = new ObjectMapper().convertValue(node, Map.class);
                } catch (Exception ignored) {}
            }
            Map<String, Object> result = graphLlmService.suggestAxioms(tenantId, axiomConfig);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_ERROR, "SUGGESTION_FAILED", KEY_MESSAGE, e.getMessage()));
        }
    }

    private String validateAxiomConfig(Map<String, Object> body) {
        if (body == null) return "body is required";
        Object subClassOf = body.get("subClassOf");
        if (subClassOf != null) {
            if (!(subClassOf instanceof List)) return "subClassOf must be an array";
            for (Object item : (List<?>) subClassOf) {
                if (!(item instanceof Map)) return "each subClassOf entry must be an object";
                Map<?, ?> entry = (Map<?, ?>) item;
                if (entry.get("child") == null || !(entry.get("child") instanceof String))
                    return "each subClassOf entry must have a string 'child' field";
                if (entry.get("parent") == null || !(entry.get("parent") instanceof String))
                    return "each subClassOf entry must have a string 'parent' field";
                if (entry.get("id") == null || !(entry.get("id") instanceof String))
                    return "each subClassOf entry must have a string 'id' field";
            }
        }
        Object layout = body.get("layout");
        if (layout != null && !(layout instanceof Map)) return "layout must be an object";
        return null;
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

            writeContentFile(tenant.resolveOwlPath(), body.owlContent);
            writeContentFile(tenant.resolveObdaPath(), body.obdaContent);

            Tenant updated = tenantPersistenceService.findById(tenantId);
            if (updated != null) {
                engineRegistry.updateEngine(tenantId, updated);
            }
            cachedSparqlService.evictForTenant(tenantId);
            ontologyGraphService.evictCache(tenantId);

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

    private void writeContentFile(String path, String content) {
        if (content == null || content.isBlank()) return;
        try {
            File file = new File(path);
            if (!file.isAbsolute()) {
                file = new File("src/main/resources", path);
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.writeString(file.toPath(), content);
            log.info("Wrote content to file: {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.warn("Failed to write content file: {}", path, e);
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
