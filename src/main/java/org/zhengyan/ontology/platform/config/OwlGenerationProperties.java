package org.zhengyan.ontology.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "ontology.owl-generation")
public class OwlGenerationProperties {

    public record ColumnOverride(String propertyName, boolean expose) {}

    public record TableOverride(String className, boolean expose, Map<String, ColumnOverride> columnOverrides) {}

    private String nameCase = "PascalCase";
    private String tableToClassPrefix = "";
    private String columnToPropertyPrefix = "";
    private String outputDir = "generated-ontologies";
    private boolean enabled = true;
    private String iriTemplate = "/{pk}";
    private String joinTableBehavior = "object-only";
    private String mappingStyle = "per-table";
    private Map<String, TableOverride> tableOverrides = new HashMap<>();

    public String getNameCase() {
        return nameCase;
    }

    public void setNameCase(String nameCase) {
        this.nameCase = nameCase;
    }

    public String getTableToClassPrefix() {
        return tableToClassPrefix;
    }

    public void setTableToClassPrefix(String tableToClassPrefix) {
        this.tableToClassPrefix = tableToClassPrefix;
    }

    public String getColumnToPropertyPrefix() {
        return columnToPropertyPrefix;
    }

    public void setColumnToPropertyPrefix(String columnToPropertyPrefix) {
        this.columnToPropertyPrefix = columnToPropertyPrefix;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIriTemplate() {
        return iriTemplate;
    }

    public void setIriTemplate(String iriTemplate) {
        this.iriTemplate = iriTemplate;
    }

    public String getJoinTableBehavior() {
        return joinTableBehavior;
    }

    public void setJoinTableBehavior(String joinTableBehavior) {
        this.joinTableBehavior = joinTableBehavior;
    }

    public String getMappingStyle() {
        return mappingStyle;
    }

    public void setMappingStyle(String mappingStyle) {
        this.mappingStyle = mappingStyle;
    }

    public Map<String, TableOverride> getTableOverrides() {
        return tableOverrides;
    }

    public void setTableOverrides(Map<String, TableOverride> tableOverrides) {
        this.tableOverrides = tableOverrides != null ? tableOverrides : new HashMap<>();
    }
}
