package org.zhengyan.ontology.platform.engine;

import org.zhengyan.ontology.platform.exception.OntologyPlatformException;
import org.zhengyan.ontology.platform.model.Tenant;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class EngineRegistry {

    private static final Logger log = LoggerFactory.getLogger(EngineRegistry.class);

    private final Map<String, OntologyEngine> engines = new ConcurrentHashMap<>();
    private final Map<String, Tenant> tenantConfigs = new ConcurrentHashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public OntologyEngine getOrCreate(Tenant tenant) {
        rwLock.readLock().lock();
        try {
            OntologyEngine existing = engines.get(tenant.getId());
            if (existing != null) return existing;
        } finally {
            rwLock.readLock().unlock();
        }

        OntopEngine engine = new OntopEngine(tenant);
        try {
            engine.initialize(tenant);
        } catch (Exception e) {
            log.warn("Failed to initialize engine for tenant: {} (will retry on apply): {}", tenant.getId(), e.getMessage());
            throw new OntologyPlatformException("Engine initialization failed for tenant: " + tenant.getId() + ": " + e.getMessage(), 500, "ENGINE_INIT_FAILED", e);
        }

        rwLock.writeLock().lock();
        try {
            OntologyEngine existing = engines.get(tenant.getId());
            if (existing != null) {
                engine.destroy();
                return existing;
            }
            log.info("Creating engine for tenant: {}", tenant.getId());
            tenantConfigs.put(tenant.getId(), tenant);
            engines.put(tenant.getId(), engine);
            return engine;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public OntologyEngine get(String tenantId) {
        rwLock.readLock().lock();
        try {
            OntologyEngine engine = engines.get(tenantId);
            if (engine == null) {
                throw new IllegalArgumentException("No engine found for tenant: " + tenantId);
            }
            return engine;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void remove(String tenantId) {
        rwLock.writeLock().lock();
        try {
            OntologyEngine engine = engines.remove(tenantId);
            tenantConfigs.remove(tenantId);
            if (engine != null) {
                engine.destroy();
                log.info("Removed engine for tenant: {}", tenantId);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void reinitialize(String tenantId) {
        Tenant tenant;
        rwLock.readLock().lock();
        try {
            tenant = tenantConfigs.get(tenantId);
        } finally {
            rwLock.readLock().unlock();
        }
        if (tenant == null) {
            throw new IllegalArgumentException("No tenant config found for reinitialization: " + tenantId);
        }
        updateEngine(tenantId, tenant);
    }

    public void updateEngine(String tenantId, Tenant tenant) {
        OntopEngine newEngine = new OntopEngine(tenant);
        try {
            newEngine.initialize(tenant);
        } catch (Exception e) {
            log.warn("Failed to initialize engine for tenant: {}: {}", tenantId, e.getMessage());
            throw new OntologyPlatformException("Engine reinitialization failed for tenant: " + tenantId + ": " + e.getMessage(), 500, "ENGINE_REINIT_FAILED", e);
        }

        rwLock.writeLock().lock();
        try {
            OntologyEngine old = engines.put(tenantId, newEngine);
            tenantConfigs.put(tenantId, tenant);
            if (old != null) {
                old.destroy();
            }
            log.info("Engine updated for tenant: {}", tenantId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public boolean contains(String tenantId) {
        rwLock.readLock().lock();
        try {
            return engines.containsKey(tenantId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public boolean isHealthy(String tenantId) {
        rwLock.readLock().lock();
        try {
            OntologyEngine engine = engines.get(tenantId);
            return engine != null && engine.isHealthy();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Map<String, OntologyEngine> getAll() {
        rwLock.readLock().lock();
        try {
            return Map.copyOf(engines);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<String> getAllEngineIds() {
        rwLock.readLock().lock();
        try {
            return List.copyOf(engines.keySet());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @PreDestroy
    public void shutdownAll() {
        log.info("Shutting down all engines...");
        rwLock.writeLock().lock();
        try {
            engines.forEach((id, engine) -> {
                try {
                    engine.destroy();
                } catch (Exception e) {
                    log.warn("Error shutting down engine for tenant: {}", id, e);
                }
            });
            engines.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
