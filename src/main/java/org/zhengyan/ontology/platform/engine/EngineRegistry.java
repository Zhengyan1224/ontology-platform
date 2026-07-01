package org.zhengyan.ontology.platform.engine;

import org.zhengyan.ontology.platform.model.Tenant;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EngineRegistry {

    private static final Logger log = LoggerFactory.getLogger(EngineRegistry.class);

    private final Map<String, OntologyEngine> engines = new ConcurrentHashMap<>();

    public OntologyEngine getOrCreate(Tenant tenant) {
        return engines.computeIfAbsent(tenant.getId(), id -> {
            log.info("Creating engine for tenant: {}", id);
            OntopEngine engine = new OntopEngine(tenant);
            try {
                engine.initialize(tenant);
                return engine;
            } catch (Exception e) {
                log.error("Failed to initialize engine for tenant: {}", id, e);
                throw new RuntimeException("Engine initialization failed for tenant: " + id, e);
            }
        });
    }

    public OntologyEngine get(String tenantId) {
        OntologyEngine engine = engines.get(tenantId);
        if (engine == null) {
            throw new IllegalArgumentException("No engine found for tenant: " + tenantId);
        }
        return engine;
    }

    public void remove(String tenantId) {
        OntologyEngine engine = engines.remove(tenantId);
        if (engine != null) {
            engine.destroy();
            log.info("Removed engine for tenant: {}", tenantId);
        }
    }

    public void reinitialize(String tenantId) {
        remove(tenantId);
        // Will be lazily recreated on next getOrCreate call
    }

    public boolean contains(String tenantId) {
        return engines.containsKey(tenantId);
    }

    public boolean isHealthy(String tenantId) {
        OntologyEngine engine = engines.get(tenantId);
        return engine != null && engine.isHealthy();
    }

    public Map<String, OntologyEngine> getAll() {
        return Map.copyOf(engines);
    }

    public List<String> getAllEngineIds() {
        return List.copyOf(engines.keySet());
    }

    @PreDestroy
    public void shutdownAll() {
        log.info("Shutting down all engines...");
        engines.forEach((id, engine) -> {
            try {
                engine.destroy();
            } catch (Exception e) {
                log.warn("Error shutting down engine for tenant: {}", id, e);
            }
        });
        engines.clear();
    }
}
