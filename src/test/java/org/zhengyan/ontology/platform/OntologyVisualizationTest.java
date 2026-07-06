package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.service.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class OntologyVisualizationTest {

    @Test
    void testGetGraphReturnsNodesAndEdges() throws Exception {
        TenantConfig tenantConfig = new TenantConfig();
        Tenant tenant = new Tenant("test", "Test", "jdbc:h2:mem:test", "org.h2.Driver", "sa", "");
        tenant.setOwlPath("ontologies/exampleBooks.owl");
        tenantConfig.setTenants(List.of(tenant));

        TenantPersistenceService tenantPersistenceService = org.mockito.Mockito.mock(TenantPersistenceService.class);
        given(tenantPersistenceService.findAll()).willReturn(List.of());

        OwlSchemaParser owlParser = new OwlSchemaParser();
        OntologyGraphService service = new OntologyGraphService(tenantConfig, tenantPersistenceService, owlParser, 300);

        Map<String, Object> result = service.getGraph("test");

        assertNotNull(result);
        assertTrue(result.containsKey("nodes"));
        assertTrue(result.containsKey("edges"));
        assertFalse(((List<?>) result.get("nodes")).isEmpty());
        assertFalse(((List<?>) result.get("edges")).isEmpty());
        assertNoDanglingEdges(result);
    }

    @Test
    void testEmptyOwlReturnsEmptyGraph() throws Exception {
        TenantConfig tenantConfig = new TenantConfig();
        Tenant emptyTenant = new Tenant("empty", "Empty", "jdbc:h2:mem:test", "org.h2.Driver", "sa", "");
        emptyTenant.setOwlPath("ontologies/nonexistent.owl");
        tenantConfig.setTenants(List.of(emptyTenant));

        TenantPersistenceService tenantPersistenceService = org.mockito.Mockito.mock(TenantPersistenceService.class);
        given(tenantPersistenceService.findAll()).willReturn(List.of());

        OwlSchemaParser owlParser = new OwlSchemaParser();
        OntologyGraphService service = new OntologyGraphService(tenantConfig, tenantPersistenceService, owlParser, 300);

        Map<String, Object> result = service.getGraph("empty");

        assertNotNull(result);
        assertTrue(((List<?>) result.get("nodes")).isEmpty());
    }

    @Test
    void testGraphIsCached() throws Exception {
        TenantConfig tenantConfig = new TenantConfig();
        Tenant tenant = new Tenant("test", "Test", "jdbc:h2:mem:test", "org.h2.Driver", "sa", "");
        tenant.setOwlPath("ontologies/exampleBooks.owl");
        tenantConfig.setTenants(List.of(tenant));

        TenantPersistenceService tenantPersistenceService = org.mockito.Mockito.mock(TenantPersistenceService.class);
        given(tenantPersistenceService.findAll()).willReturn(List.of());

        OwlSchemaParser owlParser = new OwlSchemaParser();
        OntologyGraphService service = new OntologyGraphService(tenantConfig, tenantPersistenceService, owlParser, 300);

        Map<String, Object> first = service.getGraph("test");
        Map<String, Object> second = service.getGraph("test");

        assertSame(first, second);
    }

    @SuppressWarnings("unchecked")
    private void assertNoDanglingEdges(Map<String, Object> result) {
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) result.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) result.get("edges");
        List<Object> nodeIds = nodes.stream().map(node -> node.get("id")).toList();

        for (Map<String, Object> edge : edges) {
            assertTrue(nodeIds.contains(edge.get("source")), "Missing source node: " + edge);
            assertTrue(nodeIds.contains(edge.get("target")), "Missing target node: " + edge);
        }
    }
}
