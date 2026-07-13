package org.zhengyan.ontology.platform.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class NlqExamplesController {

    private static final Logger log = LoggerFactory.getLogger(NlqExamplesController.class);

    private static final String KEY_ERROR = "error";
    private static final String KEY_MESSAGE = "message";

    private final ResourceLoader resourceLoader;

    public NlqExamplesController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @GetMapping("/tenants/{tenantId}/nlq/examples")
    public ResponseEntity<?> getExamples(@PathVariable String tenantId) {
        try {
            String location = "classpath:nlq-templates/" + tenantId + "-examples.yml";
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("tenantId", tenantId);
                empty.put("examples", List.of());
                return ResponseEntity.ok(empty);
            }
            try (InputStream is = resource.getInputStream()) {
                Yaml yaml = new Yaml();
                Map<String, Object> root = yaml.load(is);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("tenantId", tenantId);
                result.put("examples", root != null && root.get("examples") != null
                        ? root.get("examples") : List.of());
                return ResponseEntity.ok(result);
            }
        } catch (Exception e) {
            log.warn("Failed to load examples for '{}': {}", tenantId, e.getMessage());
            Map<String, String> err = new LinkedHashMap<>();
            err.put(KEY_ERROR, "LOAD_FAILED");
            err.put(KEY_MESSAGE, e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }
}
