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
import org.springframework.core.ParameterizedTypeReference;
import org.zhengyan.ontology.platform.service.AuditService;
import org.zhengyan.ontology.platform.service.TenantPersistenceService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlatformIntegrationTest {

    private static final String CLASSES = "classes";
    private static final String TEST_TENANT = "test-tenant";
    private static final String FILTER_TEST = "filter-test";
    private static final String INTEGRATION_TEST_TENANT = "integration-test-tenant";

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
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/health", HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    @Order(2)
    void tenantsEndpointReturnsBuiltInTenants() {
        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                "/api/v1/tenants", HttpMethod.GET, null, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().size() >= 2);
    }

    @Test
    @Order(3)
    void schemaEndpointReturnsStructuredData() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/tenants/university/schema", HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey(CLASSES));
        assertTrue(response.getBody().containsKey("properties"));
        assertTrue(response.getBody().containsKey("mappings"));
    }

    @Test
    @Order(4)
    void schemaEndpointForSample() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/tenants/sample/schema", HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey(CLASSES));
        List<Map<String, Object>> classes = (List<Map<String, Object>>) response.getBody().get(CLASSES);
        assertTrue(classes.stream().anyMatch(c -> c.get("name").equals("Author")));
        assertTrue(classes.stream().anyMatch(c -> c.get("name").equals("Book")));
    }

    @Test
    @Order(5)
    void auditLogRecordsAndRetrieves() {
        auditService.recordSparqlQuery(TEST_TENANT, "SELECT ?s WHERE {?s ?p ?o}",
                "SELECT 1", 5, true, null, 1);

        var logs = auditService.getLogs(TEST_TENANT, null, 10, 0);
        assertFalse(logs.isEmpty());
        assertEquals(TEST_TENANT, logs.get(0).getTenantId());
        assertEquals("SPARQL", logs.get(0).getQueryType());
        assertTrue(logs.get(0).getDurationMs() >= 0);
    }

    @Test
    @Order(6)
    void auditLogFiltersByType() {
        auditService.recordNlqQuery(FILTER_TEST, "List all books",
                "SELECT ?book WHERE {}", 10, true, null, 2);

        var nlqLogs = auditService.getLogs(FILTER_TEST, "NLQ", 10, 0);
        assertFalse(nlqLogs.isEmpty());
        assertEquals("NLQ", nlqLogs.get(0).getQueryType());

        var sparqlLogs = auditService.getLogs(FILTER_TEST, "SPARQL", 10, 0);
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
        tenant.setId(INTEGRATION_TEST_TENANT);
        tenant.setName("Integration Test");
        tenant.setJdbcUrl("jdbc:h2:mem:testdb");
        tenant.setJdbcDriver("org.h2.Driver");
        tenant.setJdbcUsername("sa");
        tenant.setJdbcPassword("");
        tenant.setOwlPath("ontologies/exampleBooks.owl");
        tenant.setObdaPath("ontologies/exampleBooks.obda");

        tenantPersistenceService.save(tenant);
        assertNotNull(tenantPersistenceService.findById(INTEGRATION_TEST_TENANT));

        var found = tenantPersistenceService.findById(INTEGRATION_TEST_TENANT);
        assertEquals("Integration Test", found.getName());

        tenantPersistenceService.deleteById(INTEGRATION_TEST_TENANT);
        assertNull(tenantPersistenceService.findById(INTEGRATION_TEST_TENANT));
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

    @Test
    @Order(10)
    void mappingDownloadOwl() {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/tenants/sample/mapping/owl", HttpMethod.GET, null, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().getContentType().toString().contains("text/turtle"));
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("Prefix") || body.contains("@prefix") || body.contains(":"));
    }

    @Test
    @Order(11)
    void mappingDownloadObda() {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/tenants/sample/mapping/obda", HttpMethod.GET, null, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().getContentType().toString().contains("text/plain"));
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("[") || body.contains("Mapping") || body.contains("mapping"));
    }

    @Test
    @Order(12)
    void savedQueryCrud() {
        String shareToken = UUID.randomUUID().toString();

        Map<String, Object> saveBody = new java.util.LinkedHashMap<>();
        saveBody.put("tenantId", "sample");
        saveBody.put("question", "List all books");
        saveBody.put("sparql", "SELECT ?book ?title WHERE { ?book a :Book . ?book :title ?title . }");
        saveBody.put("resultSummary", "5 results");

        HttpEntity<Map<String, Object>> saveEntity = new HttpEntity<>(saveBody);
        ResponseEntity<Map<String, Object>> saveResponse = rest.exchange(
                "/api/v1/saved-queries", HttpMethod.POST, saveEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertEquals(HttpStatus.CREATED, saveResponse.getStatusCode());
        assertNotNull(saveResponse.getBody());
        assertNotNull(saveResponse.getBody().get("shareToken"));
    }

    @Test
    @Order(13)
    void savedQueryListByTenant() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/tenants/sample/saved-queries", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("queries"));
        assertNotNull(response.getBody().get("total"));
    }

    @Test
    @Order(14)
    void sparqlAskQueryReturnsBoolean() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<String> entity = new HttpEntity<>(
                "{\"query\":\"ASK { ?s ?p ?o }\"}", headers);
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/tenants/sample/sparql", HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("booleanQueryResult"));
        assertTrue(response.getBody().containsKey("queryType"));
        assertEquals("BOOLEAN", response.getBody().get("queryType"));
        assertTrue((Boolean) response.getBody().get("booleanQueryResult"));
    }

    @Test
    @Order(15)
    void sparqlAskFalseQuery() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<String> entity = new HttpEntity<>(
                "{\"query\":\"ASK { ?s ?p ?o FILTER(1 = 0) }\"}", headers);
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/tenants/sample/sparql", HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse((Boolean) response.getBody().get("booleanQueryResult"));
    }

    @Test
    @Order(16)
    void queryHistoryListByTenant() {
        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                "/api/v1/tenants/sample/query-history?limit=10&offset=0", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @Order(17)
    void queryHistoryRecordsAfterSparql() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<String> entity = new HttpEntity<>(
                "{\"query\":\"SELECT ?s ?p ?o WHERE {?s ?p ?o} LIMIT 2\"}", headers);
        rest.exchange("/api/v1/tenants/sample/sparql", HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        ResponseEntity<List<Map<String, Object>>> history = rest.exchange(
                "/api/v1/tenants/sample/query-history?limit=5&offset=0", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertEquals(HttpStatus.OK, history.getStatusCode());
        assertNotNull(history.getBody());
        assertFalse(history.getBody().isEmpty());
    }

    @Test
    @Order(18)
    void generateMappingReturnsZip() {
        ResponseEntity<byte[]> response = rest.exchange(
                "/api/v1/tenants/sample/generate-mapping", HttpMethod.GET, null, byte[].class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length > 0);
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
        assertNotNull(response.getHeaders().get("X-Validation-Valid"));
    }
}
