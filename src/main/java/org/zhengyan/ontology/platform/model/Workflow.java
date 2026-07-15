package org.zhengyan.ontology.platform.model;

import java.time.LocalDateTime;

public class Workflow {
    private String id;
    private String tenantId;
    private String name;
    private String dagJson;
    private boolean enabled;
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDagJson() { return dagJson; }
    public void setDagJson(String dagJson) { this.dagJson = dagJson; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
