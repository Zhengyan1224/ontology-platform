package org.zhengyan.ontology.platform.model;

import java.util.List;

public class SparqlQueryRequest {
    private String query;
    private List<String> defaultGraphUris;
    private Integer limit;
    private Integer offset;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public List<String> getDefaultGraphUris() { return defaultGraphUris; }
    public void setDefaultGraphUris(List<String> defaultGraphUris) { this.defaultGraphUris = defaultGraphUris; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
    public Integer getOffset() { return offset; }
    public void setOffset(Integer offset) { this.offset = offset; }
}
