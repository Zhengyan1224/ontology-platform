package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.engine.OntopEngine;
import org.zhengyan.ontology.platform.model.Tenant;

import static org.junit.jupiter.api.Assertions.*;

public class Rdf4jOntologyEngineTest {

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId("test");
        tenant.setName("Test Tenant");
        tenant.setOwlPath("test.owl");
        tenant.setObdaPath("test.obda");
        tenant.setJdbcUrl("jdbc:h2:mem:test");
        tenant.setJdbcDriver("org.h2.Driver");
        tenant.setJdbcUsername("sa");
        tenant.setJdbcPassword("");
    }

    @Test
    void testEngineLifecycle() throws Exception {
        OntopEngine engine = new OntopEngine(tenant);
        assertFalse(engine.isHealthy());
        assertEquals("not_initialized", engine.checkHealth());
    }

    @Test
    void testGetTenantId() {
        OntopEngine engine = new OntopEngine(tenant);
        assertEquals("test", engine.getTenantId());
    }

    @Test
    void testGetOntologyInfo() {
        OntopEngine engine = new OntopEngine(tenant);
        var info = engine.getOntologyInfo();
        assertEquals("test", info.get("tenantId"));
        assertFalse((Boolean) info.get("initialized"));
    }
}
