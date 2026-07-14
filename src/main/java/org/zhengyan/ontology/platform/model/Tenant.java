package org.zhengyan.ontology.platform.model;

/**
 * @author 郑炎 Zheng Yan
 */
public class Tenant {
    private String id;
    private String name;
    private String owlPath;
    private String obdaPath;
    private String propertiesPath;
    private String jdbcUrl;
    private String jdbcDriver;
    private String jdbcUsername;
    private String jdbcPassword;
    private String initSql;
    private String owlContent;
    private String obdaContent;

    public Tenant() {
    }

    public Tenant(String id, String name, String jdbcUrl, String jdbcDriver,
                  String jdbcUsername, String jdbcPassword) {
        this.id = id;
        this.name = name;
        this.jdbcUrl = jdbcUrl;
        this.jdbcDriver = jdbcDriver;
        this.jdbcUsername = jdbcUsername;
        this.jdbcPassword = jdbcPassword;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOwlPath() { return owlPath; }
    public void setOwlPath(String owlPath) { this.owlPath = owlPath; }
    public String getObdaPath() { return obdaPath; }
    public void setObdaPath(String obdaPath) { this.obdaPath = obdaPath; }
    public String getPropertiesPath() { return propertiesPath; }
    public void setPropertiesPath(String propertiesPath) { this.propertiesPath = propertiesPath; }
    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    public String getJdbcDriver() { return jdbcDriver; }
    public void setJdbcDriver(String jdbcDriver) { this.jdbcDriver = jdbcDriver; }
    public String getJdbcUsername() { return jdbcUsername; }
    public void setJdbcUsername(String jdbcUsername) { this.jdbcUsername = jdbcUsername; }
    public String getJdbcPassword() { return jdbcPassword; }
    public void setJdbcPassword(String jdbcPassword) { this.jdbcPassword = jdbcPassword; }
    public String getInitSql() { return initSql; }
    public void setInitSql(String initSql) { this.initSql = initSql; }
    public String getOwlContent() { return owlContent; }
    public void setOwlContent(String owlContent) { this.owlContent = owlContent; }
    public String getObdaContent() { return obdaContent; }
    public void setObdaContent(String obdaContent) { this.obdaContent = obdaContent; }

    public String resolveOwlPath() {
        if (owlPath != null && !owlPath.isBlank()) {
            return owlPath;
        }
        return id + ".owl";
    }

    public String resolveObdaPath() {
        if (obdaPath != null && !obdaPath.isBlank()) {
            return obdaPath;
        }
        return id + ".obda";
    }
}
