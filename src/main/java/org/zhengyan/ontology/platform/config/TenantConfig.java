package org.zhengyan.ontology.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.zhengyan.ontology.platform.model.Tenant;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "ontology")
public class TenantConfig {
    private List<Tenant> tenants = new ArrayList<>();

    public List<Tenant> getTenants() { return tenants; }
    public void setTenants(List<Tenant> tenants) { this.tenants = tenants; }
}
