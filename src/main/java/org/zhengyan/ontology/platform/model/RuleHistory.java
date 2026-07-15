package org.zhengyan.ontology.platform.model;

import java.time.LocalDateTime;

public class RuleHistory {
    private String id;
    private String ruleId;
    private String tenantId;
    private String contextJson;
    private boolean passed;
    private String traceJson;
    private LocalDateTime evaluatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getContextJson() { return contextJson; }
    public void setContextJson(String contextJson) { this.contextJson = contextJson; }
    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
    public String getTraceJson() { return traceJson; }
    public void setTraceJson(String traceJson) { this.traceJson = traceJson; }
    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
