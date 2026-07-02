package org.zhengyan.ontology.platform.config;

import java.util.List;

public class AuthProperties {

    private final boolean enabled;
    private final List<String> apiKeys;

    public AuthProperties(boolean enabled, List<String> apiKeys) {
        this.enabled = enabled;
        this.apiKeys = apiKeys;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getApiKeys() {
        return apiKeys;
    }
}
