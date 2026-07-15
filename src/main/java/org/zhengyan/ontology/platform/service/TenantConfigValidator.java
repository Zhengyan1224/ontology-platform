package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zhengyan.ontology.platform.model.Tenant;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TenantConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(TenantConfigValidator.class);

    public Map<String, String> validate(Tenant tenant) {
        Map<String, String> errors = new LinkedHashMap<>();

        validateJdbc(tenant, errors);

        return errors;
    }

    private void validateJdbc(Tenant tenant, Map<String, String> errors) {
        try {
            Class.forName(tenant.getJdbcDriver());
        } catch (ClassNotFoundException e) {
            errors.put("jdbcDriver", "JDBC driver class not found: " + tenant.getJdbcDriver());
            return;
        }

        try (Connection conn = DriverManager.getConnection(
                tenant.getJdbcUrl(),
                tenant.getJdbcUsername(),
                tenant.getJdbcPassword())) {
            log.debug("JDBC connection successful for tenant [{}]", tenant.getId());
        } catch (Exception e) {
            errors.put("jdbcUrl", "JDBC connection failed: " + e.getMessage());
        }
    }

    public boolean isValid(Tenant tenant) {
        return validate(tenant).isEmpty();
    }
}
