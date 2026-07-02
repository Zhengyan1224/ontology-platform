package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "ontology.auth.enabled=true")
public class AuthIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointIsPublic() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/health", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void protectedEndpointReturns401WithoutKey() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/tenants", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void protectedEndpointReturns200WithValidKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "admin-key-001");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/tenants", HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void loginReturnsTokenWithValidCredentials() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<String> entity = new HttpEntity<>("{\"username\":\"admin\",\"password\":\"admin123\"}", headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.POST, entity, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().get("token"));
        assertEquals("ROLE_ADMIN", response.getBody().get("role"));
    }

    @Test
    void loginReturns401WithInvalidCredentials() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<String> entity = new HttpEntity<>("{\"username\":\"admin\",\"password\":\"wrong\"}", headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.POST, entity, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void jwtTokenGrantsAccessToProtectedEndpoint() {
        String token = loginAndGetToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/tenants", HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void adminKeyCanAccessAdminEndpoints() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "admin-key-001");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/api-keys", HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void devKeyCannotAccessAdminEndpoints() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "dev-key-002");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/api-keys", HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void jwtAdminCanAccessAdminEndpoints() {
        String token = loginAndGetToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/api-keys", HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private String loginAndGetToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<String> entity = new HttpEntity<>("{\"username\":\"admin\",\"password\":\"admin123\"}", headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.POST, entity, Map.class);
        return (String) response.getBody().get("token");
    }
}
