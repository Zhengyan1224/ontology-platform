package org.zhengyan.ontology.platform.config;

import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.service.TemplateLoader;
import org.zhengyan.ontology.platform.service.TenantPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class OntologyInitializer {

    private static final Logger log = LoggerFactory.getLogger(OntologyInitializer.class);

    private final TenantConfig tenantConfig;
    private final EngineRegistry engineRegistry;
    private final TenantPersistenceService tenantPersistenceService;
    private final TemplateLoader templateLoader;

    public OntologyInitializer(TenantConfig tenantConfig,
                                EngineRegistry engineRegistry,
                                TenantPersistenceService tenantPersistenceService,
                                TemplateLoader templateLoader) {
        this.tenantConfig = tenantConfig;
        this.engineRegistry = engineRegistry;
        this.tenantPersistenceService = tenantPersistenceService;
        this.templateLoader = templateLoader;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeTenants() {
        boolean anyConfigured = false;

        for (Tenant tenant : tenantConfig.getTenants()) {
            anyConfigured = true;
            initializeTenant(tenant);
            templateLoader.load(tenant.getId());
        }

        for (Tenant tenant : tenantPersistenceService.findAll()) {
            if (!engineRegistry.contains(tenant.getId())) {
                anyConfigured = true;
                initializeTenant(tenant);
                templateLoader.load(tenant.getId());
            }
        }

        if (!anyConfigured) {
            log.warn("No tenants configured. Platform will start without any ontology engine.");
        }
    }

    private void initializeTenant(Tenant tenant) {
        if (!hasContentOrFiles(tenant)) {
            log.info("Skipping engine initialization for tenant [{}] — no OWL/OBDA content or files yet", tenant.getId());
            return;
        }
        try {
            engineRegistry.getOrCreate(tenant);
            log.info("Successfully initialized tenant [{}]", tenant.getId());
        } catch (Exception e) {
            log.warn("Engine initialization deferred for tenant [{}] on startup: {}",
                    tenant.getId(), e.getMessage());
        }
    }

    private boolean hasContentOrFiles(Tenant tenant) {
        if (tenant.getOwlContent() != null && !tenant.getOwlContent().isBlank()) return true;
        if (tenant.getObdaContent() != null && !tenant.getObdaContent().isBlank()) return true;

        String owlPath = resolveFilePath(tenant.resolveOwlPath());
        String obdaPath = resolveFilePath(tenant.resolveObdaPath());
        return new File(owlPath).exists() || new File(obdaPath).exists();
    }

    private static String resolveFilePath(String path) {
        if (path == null || path.isBlank()) return path;
        File f = new File(path);
        if (!f.isAbsolute()) {
            f = new File("src/main/resources", path);
        }
        return f.getAbsolutePath();
    }
}
