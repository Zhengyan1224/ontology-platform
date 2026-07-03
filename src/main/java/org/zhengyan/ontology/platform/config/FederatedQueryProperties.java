package org.zhengyan.ontology.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ontology.federated-query")
public class FederatedQueryProperties {

    private long timeoutMs = 30000;
    private long perSubqueryTimeoutMs = 0;
    private int maxConcurrency = 4;

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public long getPerSubqueryTimeoutMs() {
        return perSubqueryTimeoutMs;
    }

    public void setPerSubqueryTimeoutMs(long perSubqueryTimeoutMs) {
        this.perSubqueryTimeoutMs = perSubqueryTimeoutMs;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }
}
