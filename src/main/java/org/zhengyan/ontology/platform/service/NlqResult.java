package org.zhengyan.ontology.platform.service;

import org.zhengyan.ontology.platform.model.SparqlQueryResult;

import java.util.List;
import java.util.Map;

public class NlqResult {
    private final String question;
    private final String sparql;
    private final String mode;
    private final List<String> variables;
    private final List<Map<String, Object>> results;
    private final long executionTimeMs;

    public NlqResult(String question, String sparql, String mode,
                     List<String> variables, List<Map<String, Object>> results,
                     long executionTimeMs) {
        this.question = question;
        this.sparql = sparql;
        this.mode = mode;
        this.variables = variables;
        this.results = results;
        this.executionTimeMs = executionTimeMs;
    }

    public static NlqResult fromSparqlResult(String question, String sparql,
                                             SparqlQueryResult sr, String mode) {
        return new NlqResult(question, sparql, mode,
                sr.getVariables(), sr.getResults(), sr.getExecutionTimeMs());
    }

    public String getQuestion() { return question; }
    public String getSparql() { return sparql; }
    public String getMode() { return mode; }
    public List<String> getVariables() { return variables; }
    public List<Map<String, Object>> getResults() { return results; }
    public long getExecutionTimeMs() { return executionTimeMs; }
}
