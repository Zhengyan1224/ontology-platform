package org.zhengyan.ontology.platform.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.service.ObdaGeneratorService;
import org.zhengyan.ontology.platform.service.ObdaMappingValidator;
import org.zhengyan.ontology.platform.service.OwlGeneratorService;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/v1")
public class GenerationController {

    private static final String KEY_ERROR = "error";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_TENANT_ID = "tenantId";
    private static final String KEY_STATUS = "status";
    private static final String TENANT_NOT_FOUND = "TENANT_NOT_FOUND";
    private static final String TENANT_NOT_FOUND_PREFIX = "Tenant not found: ";

    private final TenantConfig tenantConfig;
    private final OwlGeneratorService owlGeneratorService;
    private final ObdaGeneratorService obdaGeneratorService;
    private final ObdaMappingValidator obdaMappingValidator;

    public GenerationController(TenantConfig tenantConfig,
                                OwlGeneratorService owlGeneratorService,
                                ObdaGeneratorService obdaGeneratorService,
                                ObdaMappingValidator obdaMappingValidator) {
        this.tenantConfig = tenantConfig;
        this.owlGeneratorService = owlGeneratorService;
        this.obdaGeneratorService = obdaGeneratorService;
        this.obdaMappingValidator = obdaMappingValidator;
    }

    @Deprecated
    @PostMapping("/tenants/{tenantId}/generate-owl")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> generateOwl(@PathVariable String tenantId) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(KEY_ERROR, TENANT_NOT_FOUND));
        }
        try {
            String owl = owlGeneratorService.generateOwl(tenant);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(KEY_TENANT_ID, tenantId);
            result.put("owl", owl);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put(KEY_ERROR, "OWL_GENERATION_FAILED");
            err.put(KEY_MESSAGE, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    @GetMapping("/tenants/{tenantId}/mapping/owl")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> downloadOwl(@PathVariable String tenantId) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(KEY_ERROR, TENANT_NOT_FOUND));
        }
        try {
            String owl = owlGeneratorService.generateOwl(tenant);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/turtle"));
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + tenantId + ".owl\"");
            return ResponseEntity.ok().headers(headers).body(owl);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_ERROR, "OWL_GENERATION_FAILED", KEY_MESSAGE, e.getMessage()));
        }
    }

    @GetMapping("/tenants/{tenantId}/mapping/obda")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> downloadObda(@PathVariable String tenantId) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(KEY_ERROR, TENANT_NOT_FOUND));
        }
        try {
            String obda = obdaGeneratorService.generateObda(tenant);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/plain"));
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + tenantId + ".obda\"");
            return ResponseEntity.ok().headers(headers).body(obda);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_ERROR, "OBDA_GENERATION_FAILED", KEY_MESSAGE, e.getMessage()));
        }
    }

    @GetMapping("/tenants/{tenantId}/mapping/validate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> validateMapping(@PathVariable String tenantId) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(KEY_ERROR, TENANT_NOT_FOUND));
        }
        try {
            String obda = obdaGeneratorService.generateObda(tenant);
            ObdaMappingValidator.ValidationResult result = obdaMappingValidator.validate(tenant, obda);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put(KEY_ERROR, "VALIDATION_FAILED");
            err.put(KEY_MESSAGE, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    @GetMapping("/tenants/{tenantId}/generate-mapping")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> generateMapping(@PathVariable String tenantId) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(KEY_ERROR, TENANT_NOT_FOUND));
        }
        try {
            String owl = owlGeneratorService.generateOwl(tenant);
            String obda = obdaGeneratorService.generateObda(tenant);

            ObdaMappingValidator.ValidationResult validation = obdaMappingValidator.validate(tenant, obda);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                ZipEntry owlEntry = new ZipEntry(tenantId + ".owl");
                zos.putNextEntry(owlEntry);
                zos.write(owl.getBytes());
                zos.closeEntry();

                ZipEntry obdaEntry = new ZipEntry(tenantId + ".obda");
                zos.putNextEntry(obdaEntry);
                zos.write(obda.getBytes());
                zos.closeEntry();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", tenantId + "-mapping.zip");
            headers.add("X-Validation-Valid", String.valueOf(validation.valid()));
            headers.add("X-Validation-Errors", String.join(", ", validation.errors()));
            headers.add("X-Validation-Warnings", String.join(", ", validation.warnings()));

            return ResponseEntity.ok().headers(headers).body(baos.toByteArray());
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put(KEY_ERROR, "MAPPING_GENERATION_FAILED");
            err.put(KEY_MESSAGE, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    private Tenant findTenant(String tenantId) {
        List<Tenant> all = tenantConfig.getTenants();
        return all.stream()
                .filter(t -> t.getId().equals(tenantId))
                .findFirst()
                .orElse(null);
    }
}
