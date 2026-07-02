package org.zhengyan.ontology.platform.model;

import jakarta.validation.constraints.NotBlank;

public class NlqRequest {
    @NotBlank
    private String question;
    private String sessionId;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
