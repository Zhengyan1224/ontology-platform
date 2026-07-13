package org.zhengyan.ontology.platform.model;

import org.eclipse.rdf4j.model.Model;

import java.util.List;
import java.util.Map;

public class SparqlQueryResult {
    public enum QueryType {
        SELECT, CONSTRUCT, DESCRIBE, BOOLEAN
    }

    private QueryType queryType = QueryType.SELECT;
    private List<String> variables;
    private List<Map<String, Object>> results;
    private Model graphModel;
    private boolean booleanQueryResult;
    private long executionTimeMs;
    private String translatedSql;

    public SparqlQueryResult() {
    }

    public SparqlQueryResult(List<String> variables, List<Map<String, Object>> results, long executionTimeMs) {
        this.queryType = QueryType.SELECT;
        this.variables = variables;
        this.results = results;
        this.executionTimeMs = executionTimeMs;
    }

    public SparqlQueryResult(Model graphModel, long executionTimeMs) {
        this(QueryType.CONSTRUCT, graphModel, executionTimeMs);
    }

    public SparqlQueryResult(QueryType queryType, Model graphModel, long executionTimeMs) {
        this.queryType = queryType;
        this.graphModel = graphModel;
        this.executionTimeMs = executionTimeMs;
    }

    public SparqlQueryResult(boolean booleanQueryResult, long executionTimeMs) {
        this.queryType = QueryType.BOOLEAN;
        this.booleanQueryResult = booleanQueryResult;
        this.executionTimeMs = executionTimeMs;
    }

    public boolean isGraphResult() {
        return graphModel != null;
    }

    public boolean isBooleanResult() {
        return queryType == QueryType.BOOLEAN;
    }

    public QueryType getQueryType() { return queryType; }
    public void setQueryType(QueryType queryType) { this.queryType = queryType; }
    public List<String> getVariables() { return variables; }
    public void setVariables(List<String> variables) { this.variables = variables; }
    public List<Map<String, Object>> getResults() { return results; }
    public void setResults(List<Map<String, Object>> results) { this.results = results; }
    public Model getGraphModel() { return graphModel; }
    public void setGraphModel(Model graphModel) { this.graphModel = graphModel; }
    public boolean isBooleanQueryResult() { return booleanQueryResult; }
    public void setBooleanQueryResult(boolean booleanQueryResult) { this.booleanQueryResult = booleanQueryResult; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    public String getTranslatedSql() { return translatedSql; }
    public void setTranslatedSql(String translatedSql) { this.translatedSql = translatedSql; }
}
