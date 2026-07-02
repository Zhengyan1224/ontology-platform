package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.zhengyan.ontology.platform.service.AuditService;
import org.zhengyan.ontology.platform.service.TenantPersistenceService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlatformIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AuditService auditService;

    @Autowired
    private TenantPersistenceService tenantPersistenceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    void healthEndpointWorks() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/health", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    @Order(2)
    void tenantsEndpointReturnsBuiltInTenants() {
        ResponseEntity<List> response = rest.getForEntity("/api/v1/tenants", List.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().size() >= 2);
    }

    @Test
    @Order(3)
    void schemaEndpointReturnsStructuredData() {
        ResponseEntity<Map> response = rest.getForEntity(
                "/api/v1/tenants/university/schema", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("classes"));
        assertTrue(response.getBody().containsKey("properties"));
        assertTrue(response.getBody().containsKey("mappings"));
    }

    @Test
    @Order(4)
    void schemaEndpointForSample() {
        ResponseEntity<Map> response = rest.getForEntity(
                "/api/v1/tenants/sample/schema", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("classes"));
        List<Map<String, Object>> classes = (List<Map<String, Object>>) response.getBody().get("classes");
        assertTrue(classes.stream().anyMatch(c -> c.get("name").equals("Author")));
        assertTrue(classes.stream().anyMatch(c -> c.get("name").equals("Book")));
    }

    @Test
    @Order(5)
    void auditLogRecordsAndRetrieves() {
        auditService.recordSparqlQuery("test-tenant", "SELECT ?s WHERE {?s ?p ?o}",
                "SELECT 1", 5, true, null, 1);

        var logs = auditService.getLogs("test-tenant", null, 10, 0);
        assertFalse(logs.isEmpty());
        assertEquals("test-tenant", logs.get(0).getTenantId());
        assertEquals("SPARQL", logs.get(0).getQueryType());
        assertTrue(logs.get(0).getDurationMs() >= 0);
    }

    @Test
    @Order(6)
    void auditLogFiltersByType() {
        auditService.recordNlqQuery("filter-test", "List all books",
                "SELECT ?book WHERE {}", 10, true, null, 2);

        var nlqLogs = auditService.getLogs("filter-test", "NLQ", 10, 0);
        assertFalse(nlqLogs.isEmpty());
        assertEquals("NLQ", nlqLogs.get(0).getQueryType());

        var sparqlLogs = auditService.getLogs("filter-test", "SPARQL", 10, 0);
        assertTrue(sparqlLogs.isEmpty());
    }

    @Test
    @Order(7)
    void auditLogClearWorks() {
        auditService.recordSparqlQuery("clear-test", "SELECT 1", null, 1, true, null, 0);
        assertTrue(auditService.getTotalCount() > 0);

        auditService.clearLogs();
        assertEquals(0, auditService.getTotalCount());
    }

    @Test
    @Order(8)
    void tenantPersistenceCrud() {
        var tenant = new org.zhengyan.ontology.platform.model.Tenant();
        tenant.setId("integration-test-tenant");
        tenant.setName("Integration Test");
        tenant.setJdbcUrl("jdbc:h2:mem:testdb");
        tenant.setJdbcDriver("org.h2.Driver");
        tenant.setJdbcUsername("sa");
        tenant.setJdbcPassword("");
        tenant.setOwlPath("ontologies/exampleBooks.owl");
        tenant.setObdaPath("ontologies/exampleBooks.obda");

        tenantPersistenceService.save(tenant);
        assertNotNull(tenantPersistenceService.findById("integration-test-tenant"));

        var found = tenantPersistenceService.findById("integration-test-tenant");
        assertEquals("Integration Test", found.getName());

        tenantPersistenceService.deleteById("integration-test-tenant");
        assertNull(tenantPersistenceService.findById("integration-test-tenant"));
    }

    @Test
    @Order(9)
    void sparqlCsvFormat() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT, "text/csv");
        HttpEntity<String> entity = new HttpEntity<>(
                "{\"query\":\"SELECT ?s ?p ?o WHERE {?s ?p ?o} LIMIT 1\"}", headers);
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/tenants/sample/sparql", HttpMethod.POST, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().getContentType().toString().contains("text/csv"));
        assertNotNull(response.getBody());
    }
}
