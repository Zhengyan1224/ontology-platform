package org.zhengyan.ontology.platform.model;

import java.util.List;
import java.util.Map;

public class SparqlQueryResult {
    private List<String> variables;
    private List<Map<String, Object>> results;
    private long executionTimeMs;
    private String translatedSql;

    public SparqlQueryResult() {
    }

    public SparqlQueryResult(List<String> variables, List<Map<String, Object>> results, long executionTimeMs) {
        this.variables = variables;
        this.results = results;
        this.executionTimeMs = executionTimeMs;
    }

    public List<String> getVariables() { return variables; }
    public void setVariables(List<String> variables) { this.variables = variables; }
    public List<Map<String, Object>> getResults() { return results; }
    public void setResults(List<Map<String, Object>> results) { this.results = results; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    public String getTranslatedSql() { return translatedSql; }
    public void setTranslatedSql(String translatedSql) { this.translatedSql = translatedSql; }
}
