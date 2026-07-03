package org.zhengyan.ontology.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ontology.owl-generation")
public class OwlGenerationProperties {

    private String nameCase = "PascalCase";
    private String tableToClassPrefix = "";
    private String columnToPropertyPrefix = "";
    private String outputDir = "generated-ontologies";
    private boolean enabled = true;

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
}
