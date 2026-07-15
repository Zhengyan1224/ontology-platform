package org.zhengyan.ontology.platform.model;

import java.time.LocalDateTime;

public class OntologyProposal {

    private String id;
    private String tenantId;
    private String title;
    private String description;
    private String proposedOwl;
    private String proposedObda;
    private String status;
    private String source;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getProposedOwl() { return proposedOwl; }
    public void setProposedOwl(String proposedOwl) { this.proposedOwl = proposedOwl; }
    public String getProposedObda() { return proposedObda; }
    public void setProposedObda(String proposedObda) { this.proposedObda = proposedObda; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
