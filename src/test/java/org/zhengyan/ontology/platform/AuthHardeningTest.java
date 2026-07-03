package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
    "ontology.auth.enabled=true",
    "ontology.rate-limit.capacity=5",
    "ontology.rate-limit.refill-tokens=5",
    "ontology.rate-limit.refill-period-seconds=60"
})
public class AuthHardeningTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String API_KEY_HEADER = "X-API-Key";

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM jwt_blacklist");
        jdbcTemplate.execute("DELETE FROM audit_logs");
        jdbcTemplate.execute("UPDATE api_keys SET enabled = TRUE");
    }

    @Test
    void rateLimitExhausted() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CONTENT_TYPE, APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"username\":\"admin\",\"password\":\"admin123\"}", headers);

        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = restTemplate.exchange(
                    LOGIN_URL, HttpMethod.POST, entity, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        ResponseEntity<String> response = restTemplate.exchange(
                LOGIN_URL, HttpMethod.POST, entity, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
    }

    @Test
    void jwtBlacklistAfterRevokeAll() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CONTENT_TYPE, APPLICATION_JSON);
        HttpEntity<String> loginEntity = new HttpEntity<>("{\"username\":\"admin\",\"password\":\"admin123\"}", headers);
        ResponseEntity<Map<String, Object>> loginResponse = restTemplate.exchange(
                LOGIN_URL, HttpMethod.POST, loginEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        String token = (String) loginResponse.getBody().get("token");

        HttpEntity<String> revokeEntity = new HttpEntity<>("{\"username\":\"admin\"}", headers);
        ResponseEntity<Map<String, Object>> revokeResponse = restTemplate.exchange(
                "/api/v1/auth/revoke-all", HttpMethod.POST, revokeEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertEquals(HttpStatus.OK, revokeResponse.getStatusCode());
        assertEquals("revoked", revokeResponse.getBody().get("status"));

        HttpHeaders jwtHeaders = new HttpHeaders();
        jwtHeaders.set("Authorization", "Bearer " + token);
        HttpEntity<Void> jwtEntity = new HttpEntity<>(jwtHeaders);
        ResponseEntity<String> protectedResponse = restTemplate.exchange(
                "/api/v1/tenants", HttpMethod.GET, jwtEntity, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, protectedResponse.getStatusCode());
    }

    @Test
    void apiKeyRevocation() throws Exception {
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.set(API_KEY_HEADER, "admin-key-001");
        HttpEntity<Void> adminEntity = new HttpEntity<>(adminHeaders);

        ResponseEntity<List<Map<String, Object>>> listResponse = restTemplate.exchange(
                "/api/v1/api-keys", HttpMethod.GET, adminEntity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());

        Long keyId = null;
        for (Map<String, Object> key : listResponse.getBody()) {
            if ("seeded-admin-key-001".equals(key.get("name"))) {
                keyId = ((Number) key.get("id")).longValue();
                break;
            }
        }

        ResponseEntity<Map<String, Object>> revokeResponse = restTemplate.exchange(
                "/api/v1/api-keys/" + keyId + "/revoke", HttpMethod.POST, adminEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertEquals(HttpStatus.OK, revokeResponse.getStatusCode());
        assertEquals("revoked", revokeResponse.getBody().get("status"));
    }

    @Test
    void auditLogOnAuthFailure() throws Exception {
        HttpHeaders badKeyHeaders = new HttpHeaders();
        badKeyHeaders.set(API_KEY_HEADER, "invalid-key-999");
        HttpEntity<Void> badKeyEntity = new HttpEntity<>(badKeyHeaders);
        restTemplate.exchange("/api/v1/tenants", HttpMethod.GET, badKeyEntity, String.class);

        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.set(API_KEY_HEADER, "admin-key-001");
        HttpEntity<Void> adminEntity = new HttpEntity<>(adminHeaders);
        ResponseEntity<List<Map<String, Object>>> auditResponse = restTemplate.exchange(
                "/api/v1/audit-log", HttpMethod.GET, adminEntity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertEquals(HttpStatus.OK, auditResponse.getStatusCode());

        boolean found = auditResponse.getBody().stream()
                .anyMatch(log -> "AUTH".equals(log.get("queryType"))
                        && Boolean.FALSE.equals(log.get("success")));
        assertTrue(found);
    }
}
