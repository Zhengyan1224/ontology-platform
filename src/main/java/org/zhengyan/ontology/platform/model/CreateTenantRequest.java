package org.zhengyan.ontology.platform.model;

import jakarta.validation.constraints.NotBlank;

public class CreateTenantRequest {
    @NotBlank
    private String id;

    @NotBlank
    private String name;

    @NotBlank
    private String jdbcUrl;

    @NotBlank
    private String jdbcDriver;

    private String jdbcUsername;
    private String jdbcPassword;
    private String owlPath;
    private String obdaPath;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    public String getJdbcDriver() { return jdbcDriver; }
    public void setJdbcDriver(String jdbcDriver) { this.jdbcDriver = jdbcDriver; }
    public String getJdbcUsername() { return jdbcUsername; }
    public void setJdbcUsername(String jdbcUsername) { this.jdbcUsername = jdbcUsername; }
    public String getJdbcPassword() { return jdbcPassword; }
    public void setJdbcPassword(String jdbcPassword) { this.jdbcPassword = jdbcPassword; }
    public String getOwlPath() { return owlPath; }
    public void setOwlPath(String owlPath) { this.owlPath = owlPath; }
    public String getObdaPath() { return obdaPath; }
    public void setObdaPath(String obdaPath) { this.obdaPath = obdaPath; }
}
