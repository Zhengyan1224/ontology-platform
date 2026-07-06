package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.zhengyan.ontology.platform.config.OwlGenerationProperties;
import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.service.JdbcMetadataReader;
import org.zhengyan.ontology.platform.service.MappingAssistantService;
import org.zhengyan.ontology.platform.service.ObdaGeneratorService;
import org.zhengyan.ontology.platform.service.OwlGeneratorService;
import org.zhengyan.ontology.platform.service.TenantPersistenceService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class MappingAssistantServiceTest {

    @Test
    void createsRuleBasedDraftWithoutApplyingChanges() throws Exception {
        String url = "jdbc:h2:mem:mappingassistant" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE categories (id INT PRIMARY KEY, name VARCHAR(255))");
            stmt.execute("CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(255), api_token VARCHAR(255), category_id INT)");
            stmt.execute("ALTER TABLE products ADD FOREIGN KEY (category_id) REFERENCES categories(id)");
        }

        Tenant tenant = new Tenant("product", "Product System", url, "org.h2.Driver", "sa", "");
        TenantConfig tenantConfig = new TenantConfig();
        tenantConfig.setTenants(List.of(tenant));

        TenantPersistenceService tenantPersistenceService = mock(TenantPersistenceService.class);
        given(tenantPersistenceService.findAll()).willReturn(List.of());

        JdbcMetadataReader metadataReader = new JdbcMetadataReader();
        OwlGenerationProperties properties = new OwlGenerationProperties();
        MappingAssistantService service = new MappingAssistantService(
                tenantConfig,
                tenantPersistenceService,
                new OwlGeneratorService(properties, metadataReader),
                new ObdaGeneratorService(properties, metadataReader),
                metadataReader,
                "sk-placeholder",
                "gpt-4o-mini",
                "");

        MappingAssistantService.DraftResponse response = service.createDraft(
                "product",
                new MappingAssistantService.DraftRequest(
                        "商品中心，products 是商品主表。",
                        "security",
                        true,
                        true,
                        14000));

        assertEquals("product", response.tenantId());
        assertEquals("rules", response.mode());
        assertTrue(response.draftOnly());
        assertFalse(response.applied());
        assertNotNull(response.owlDraft());
        assertNotNull(response.obdaDraft());
        assertTrue(response.metadataSummary().contains("PRODUCTS"));
        assertTrue(response.owlDraft().contains(":Product rdf:type owl:Class"));
        assertTrue(response.obdaDraft().contains("[MappingDeclaration]"));
        assertTrue(response.warnings().stream().anyMatch(w -> w.contains("api_token") || w.contains("API_TOKEN")));
    }
}
