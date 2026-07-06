package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MappingAssistantControllerTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void draftEndpointReturnsDraftOnlyResponse() {
        Map<String, Object> request = Map.of(
                "businessContext", "Books demo",
                "focus", "validation",
                "includeDraftFiles", false,
                "useLlm", false);

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/tenants/sample/mapping-assistant/draft",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("sample", response.getBody().get("tenantId"));
        assertEquals(true, response.getBody().get("draftOnly"));
        assertEquals(false, response.getBody().get("applied"));
        assertNotNull(response.getBody().get("reviewMarkdown"));
        assertNotNull(response.getBody().get("metadataSummary"));
        assertNull(response.getBody().get("owlDraft"));
        assertNull(response.getBody().get("obdaDraft"));
    }
}
