package org.zhengyan.ontology.platform.model;

import java.time.LocalDateTime;

/**
 * @author 郑炎 Zheng Yan
 */
public class SavedQuery {
    private Long id;
    private String tenantId;
    private String question;
    private String sparql;
    private String resultSummary;
    private String shareToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SavedQuery() {}

    public SavedQuery(Long id, String tenantId, String question, String sparql,
                      String resultSummary, String shareToken,
                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.question = question;
        this.sparql = sparql;
        this.resultSummary = resultSummary;
        this.shareToken = shareToken;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getSparql() { return sparql; }
    public void setSparql(String sparql) { this.sparql = sparql; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
