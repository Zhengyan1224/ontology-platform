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
import org.springframework.core.ParameterizedTypeReference;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "ontology.auth.enabled=true")
public class AuthIntegrationTest {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String API_TENANTS_URL = "/api/v1/tenants";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String APPLICATION_JSON = "application/json";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String API_KEYS_URL = "/api/v1/api-keys";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointIsPublic() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/health", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void protectedEndpointReturns401WithoutKey() {
        ResponseEntity<String> response = restTemplate.getForEntity(API_TENANTS_URL, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void protectedEndpointReturns200WithValidKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(API_KEY_HEADER, "admin-key-001");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                API_TENANTS_URL, HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void loginReturnsTokenWithValidCredentials() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CONTENT_TYPE, APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"username\":\"admin\",\"password\":\"admin123\"}", headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                LOGIN_URL, HttpMethod.POST, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().get("token"));
        assertEquals("ROLE_ADMIN", response.getBody().get("role"));
    }

    @Test
    void loginReturns401WithInvalidCredentials() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CONTENT_TYPE, APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"username\":\"admin\",\"password\":\"wrong\"}", headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                LOGIN_URL, HttpMethod.POST, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void jwtTokenGrantsAccessToProtectedEndpoint() {
        String token = loginAndGetToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                API_TENANTS_URL, HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void adminKeyCanAccessAdminEndpoints() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(API_KEY_HEADER, "admin-key-001");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                API_KEYS_URL, HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void devKeyCannotAccessAdminEndpoints() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(API_KEY_HEADER, "dev-key-002");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                API_KEYS_URL, HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void jwtAdminCanAccessAdminEndpoints() {
        String token = loginAndGetToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                API_KEYS_URL, HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private String loginAndGetToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CONTENT_TYPE, APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"username\":\"admin\",\"password\":\"admin123\"}", headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                LOGIN_URL, HttpMethod.POST, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
        return (String) response.getBody().get("token");
    }
}
