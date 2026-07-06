package org.zhengyan.ontology.platform.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.zhengyan.ontology.platform.exception.OntologyPlatformException;

@Component
public class SecretsValidator {

    private static final Logger log = LoggerFactory.getLogger(SecretsValidator.class);
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    private static final String DEFAULT_JWT_SECRET = "ontology-platform-jwt-secret-key-min-256-bits-long-for-hs256";
    private static final String DEFAULT_LLM_API_KEY = "sk-placeholder";
    private static final String[] DEFAULT_SEED_KEYS = {"admin-key-001", "dev-key-002"};

    private final AuthProperties authProperties;
    private final String llmApiKey;

    public SecretsValidator(AuthProperties authProperties,
                            @Value("${ontology.nlq.llm.api-key:}") String llmApiKey) {
        this.authProperties = authProperties;
        this.llmApiKey = llmApiKey;
    }

    @PostConstruct
    public void validateSecrets() {
        boolean hasDefault = false;

        if (DEFAULT_ADMIN_PASSWORD.equals(authProperties.getAdminPassword())) {
            log.warn("Default admin password detected: '{}'", DEFAULT_ADMIN_PASSWORD);
            hasDefault = true;
        }

        if (authProperties.getJwt() != null && DEFAULT_JWT_SECRET.equals(authProperties.getJwt().getSecret())) {
            log.warn("Default JWT secret detected");
            hasDefault = true;
        }

        for (String key : authProperties.getApiKeys()) {
            for (String defaultKey : DEFAULT_SEED_KEYS) {
                if (defaultKey.equals(key)) {
                    log.warn("Default API key detected: '{}'", key);
                    hasDefault = true;
                }
            }
        }

        if (DEFAULT_LLM_API_KEY.equals(llmApiKey)) {
            log.warn("Default LLM API key detected: '{}'", DEFAULT_LLM_API_KEY);
            hasDefault = true;
        }

        if (hasDefault) {
            String message = "Default secrets detected. Set secure values via environment variables "
                    + "(ADMIN_PASSWORD, JWT_SECRET, LLM_API_KEY) or update ontology.auth.api-keys in production.";
            if (authProperties.isStrictMode()) {
                throw new OntologyPlatformException(message, 500, "DEFAULT_SECRETS");
            }
            log.warn(message);
        }
    }
}
