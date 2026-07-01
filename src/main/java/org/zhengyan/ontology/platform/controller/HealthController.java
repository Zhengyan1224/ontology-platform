package org.zhengyan.ontology.platform.controller;

import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final EngineRegistry engineRegistry;

    public HealthController(EngineRegistry engineRegistry) {
        this.engineRegistry = engineRegistry;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("timestamp", LocalDateTime.now().toString());

        Map<String, String> engines = new LinkedHashMap<>();
        engineRegistry.getAllEngineIds().forEach(id ->
                engines.put(id, engineRegistry.get(id).checkHealth()));
        status.put("engines", engines);

        return ResponseEntity.ok(status);
    }
}
