package org.zhengyan.ontology.platform.model;

import jakarta.validation.constraints.NotBlank;

public class NlqRequest {
    @NotBlank
    private String question;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
}
