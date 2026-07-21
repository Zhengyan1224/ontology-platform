package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.zhengyan.ontology.platform.config.OwlGenerationProperties;
import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.repository.TenantContentRepository;
import org.zhengyan.ontology.platform.service.JdbcMetadataReader;
import org.zhengyan.ontology.platform.service.MappingAssistantService;
import org.zhengyan.ontology.platform.service.ObdaGeneratorService;
import org.zhengyan.ontology.platform.service.OwlGeneratorService;
import org.zhengyan.ontology.platform.service.TenantPersistenceService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class MappingAssistantServiceTest {

    private TenantContentRepository contentRepo;

    @BeforeEach
    void setUp() {
        contentRepo = mock(TenantContentRepository.class);
    }

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
                new OwlGeneratorService(properties, metadataReader, contentRepo),
                new ObdaGeneratorService(properties, metadataReader),
                metadataReader,
                properties,
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

        assertNotNull(response.editableConfig());
        assertFalse(response.editableConfig().tables().isEmpty());
        assertTrue(response.editableConfig().tables().stream()
                .anyMatch(t -> t.tableName().equalsIgnoreCase("PRODUCTS") || t.tableName().equalsIgnoreCase("products") || t.tableName().equalsIgnoreCase("PRODUCTS")));
        assertTrue(response.editableConfig().tables().stream()
                .anyMatch(t -> t.className().equals("Product")));
    }

    @Test
    void applyConfigOverridesClassNameInOwlAndObda() throws Exception {
        String url = "jdbc:h2:mem:overridetest" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE categories (id INT PRIMARY KEY, name VARCHAR(255))");
            stmt.execute("CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(255), category_id INT)");
            stmt.execute("ALTER TABLE products ADD FOREIGN KEY (category_id) REFERENCES categories(id)");
        }

        Tenant tenant = new Tenant("override-test", "Override Test", url, "org.h2.Driver", "sa", "");
        TenantConfig tenantConfig = new TenantConfig();
        tenantConfig.setTenants(List.of(tenant));

        TenantPersistenceService tenantPersistenceService = mock(TenantPersistenceService.class);
        given(tenantPersistenceService.findAll()).willReturn(List.of());

        JdbcMetadataReader metadataReader = new JdbcMetadataReader();
        OwlGenerationProperties properties = new OwlGenerationProperties();
        MappingAssistantService service = new MappingAssistantService(
                tenantConfig,
                tenantPersistenceService,
                new OwlGeneratorService(properties, metadataReader, contentRepo),
                new ObdaGeneratorService(properties, metadataReader),
                metadataReader,
                properties,
                "sk-placeholder",
                "gpt-4o-mini",
                "");

        MappingAssistantService.EditableConfig config = new MappingAssistantService.EditableConfig(
                List.of(
                        new MappingAssistantService.EditableTableConfig(
                                "PRODUCTS", "CustomProduct", "CustomProduct",
                                "/{pk}", true, List.of(
                                new MappingAssistantService.EditableColumnConfig("ID", "id", "id", true, false, true),
                                new MappingAssistantService.EditableColumnConfig("NAME", "name", "productName", false, false, true),
                                new MappingAssistantService.EditableColumnConfig("CATEGORY_ID", "categoryId", "categoryId", false, true, true)
                        )),
                        new MappingAssistantService.EditableTableConfig(
                                "CATEGORIES", "CustomCategory", "CustomCategory",
                                "/{pk}", true, List.of(
                                new MappingAssistantService.EditableColumnConfig("ID", "id", "id", true, false, true),
                                new MappingAssistantService.EditableColumnConfig("NAME", "name", "categoryName", false, false, true)
                        ))
                ),
                List.of()
        );

        MappingAssistantService.DraftResponse response = service.applyConfig("override-test", config);

        assertEquals("override-test", response.tenantId());
        assertNotNull(response.owlDraft());
        assertNotNull(response.obdaDraft());
        assertTrue(response.owlDraft().contains(":CustomProduct rdf:type owl:Class"),
                "OWL should contain overridden class name CustomProduct but got:\n" + response.owlDraft());
        assertTrue(response.obdaDraft().contains("CustomProduct"),
                "OBDA should contain overridden class name CustomProduct but got:\n" + response.obdaDraft());

        assertNotNull(response.editableConfig());
        assertTrue(response.editableConfig().tables().stream()
                .anyMatch(t -> t.className().equals("CustomProduct")));
    }
}
