package org.zhengyan.ontology.platform.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.zhengyan.ontology.platform.service.ApiKeyService;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "ontology.auth.enabled", havingValue = "true", matchIfMissing = true)
public class ApiKeySeeder {

    private static final Logger log = LoggerFactory.getLogger(ApiKeySeeder.class);

    private final ApiKeyService apiKeyService;
    private final AuthProperties authProperties;

    private static final Map<String, String> DEFAULT_KEY_ROLES = Map.of(
            "admin-key-001", "ROLE_ADMIN",
            "dev-key-002", "ROLE_DEV"
    );

    public ApiKeySeeder(ApiKeyService apiKeyService, AuthProperties authProperties) {
        this.apiKeyService = apiKeyService;
        this.authProperties = authProperties;
    }

    @PostConstruct
    public void seedApiKeys() {
        if (!authProperties.isEnabled()) {
            log.info("Auth disabled, skipping API key seeding");
            return;
        }
        for (String key : authProperties.getApiKeys()) {
            String role = DEFAULT_KEY_ROLES.getOrDefault(key, "ROLE_READONLY");
            apiKeyService.seedKey(key, "seeded-" + key, role);
        }
        log.info("Seeded {} API keys from configuration", authProperties.getApiKeys().size());
    }
}
