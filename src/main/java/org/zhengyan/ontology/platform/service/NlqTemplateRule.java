package org.zhengyan.ontology.platform.service;

import java.util.List;
import java.util.Map;

public class NlqTemplateRule {
    private List<String> patterns;
    private String sparql;
    private List<Map<String, Integer>> params;
    private String description;

    public List<String> getPatterns() { return patterns; }
    public void setPatterns(List<String> patterns) { this.patterns = patterns; }
    public String getSparql() { return sparql; }
    public void setSparql(String sparql) { this.sparql = sparql; }
    public List<Map<String, Integer>> getParams() { return params; }
    public void setParams(List<Map<String, Integer>> params) { this.params = params; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
