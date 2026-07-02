package org.zhengyan.ontology.platform.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "ontology.auth")
public class AuthProperties {

    private static final Logger log = LoggerFactory.getLogger(AuthProperties.class);
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    private static final String DEFAULT_JWT_SECRET = "ontology-platform-jwt-secret-key-min-256-bits-long-for-hs256";

    private boolean enabled = true;
    private List<String> apiKeys = new ArrayList<>();
    private String adminPassword;
    private JwtConfig jwt = new JwtConfig();

    @PostConstruct
    public void warnIfDefaultSecrets() {
        if (DEFAULT_ADMIN_PASSWORD.equals(adminPassword)) {
            log.warn("=======================================================================");
            log.warn("SECURITY WARNING: Using default admin password '{}'", DEFAULT_ADMIN_PASSWORD);
            log.warn("Set the ADMIN_PASSWORD environment variable to a secure value in production.");
            log.warn("=======================================================================");
        }
        if (DEFAULT_JWT_SECRET.equals(jwt.getSecret())) {
            log.warn("=======================================================================");
            log.warn("SECURITY WARNING: Using default JWT signing secret.");
            log.warn("Set the JWT_SECRET environment variable to a secure value in production.");
            log.warn("=======================================================================");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(List<String> apiKeys) {
        this.apiKeys = apiKeys;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public JwtConfig getJwt() {
        return jwt;
    }

    public void setJwt(JwtConfig jwt) {
        this.jwt = jwt;
    }

    public static class JwtConfig {
        private String secret;
        private long expirationMs = 86400000;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMs() {
            return expirationMs;
        }

        public void setExpirationMs(long expirationMs) {
            this.expirationMs = expirationMs;
        }
    }
}
