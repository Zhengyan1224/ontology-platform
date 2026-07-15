package org.zhengyan.ontology.platform.model;

import java.time.LocalDateTime;

public class Action {
    private String id;
    private String tenantId;
    private String name;
    private String type;
    private String configJson;
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
