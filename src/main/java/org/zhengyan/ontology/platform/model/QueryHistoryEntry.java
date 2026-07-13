package org.zhengyan.ontology.platform.model;

import java.time.LocalDateTime;

public class QueryHistoryEntry {
    private Long id;
    private String tenantId;
    private Long apiKeyId;
    private String sparql;
    private long executionTimeMs;
    private LocalDateTime createdAt;

    public QueryHistoryEntry() {
    }

    public QueryHistoryEntry(String tenantId, Long apiKeyId, String sparql, long executionTimeMs) {
        this.tenantId = tenantId;
        this.apiKeyId = apiKeyId;
        this.sparql = sparql;
        this.executionTimeMs = executionTimeMs;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public Long getApiKeyId() { return apiKeyId; }
    public void setApiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; }
    public String getSparql() { return sparql; }
    public void setSparql(String sparql) { this.sparql = sparql; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
