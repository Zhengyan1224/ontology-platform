package org.zhengyan.ontology.platform.service;

import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.model.Tenant;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OntologySchemaProvider {

    private final TenantConfig tenantConfig;

    public OntologySchemaProvider(TenantConfig tenantConfig) {
        this.tenantConfig = tenantConfig;
    }

    public Map<String, String> getSchemaDescriptions() {
        Map<String, String> schemas = new LinkedHashMap<>();
        for (Tenant tenant : tenantConfig.getTenants()) {
            schemas.put(tenant.getId(), describeTenant(tenant));
        }
        return schemas;
    }

    public String getSchemaForTenant(String tenantId) {
        return tenantConfig.getTenants().stream()
                .filter(t -> t.getId().equals(tenantId))
                .findFirst()
                .map(this::describeTenant)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + tenantId));
    }

    private String describeTenant(Tenant tenant) {
        if ("sample".equals(tenant.getId())) {
            return """
                    PREFIX: <http://meraka/moss/exampleBooks.owl#>
                    Classes: :Author, :Book
                    Properties: :name (author name), :title (book title), :writtenBy
                    Mappings:
                      - tb_authors (au_code, au_name) -> :Author
                      - tb_books (bk_code, bk_title, wr_code) -> :Book
                      - tb_affiliated_writers (wr_code, wr_name) -> :AffiliatedWriter
                    Tables: tb_authors, tb_books, tb_affiliated_writers
                    """;
        }
        if ("university".equals(tenant.getId())) {
            return """
                    PREFIX: <http://example.org/university#>
                    Classes:
                      - :Person (top-level, direct instances = employees)
                      - :Employee (subclass of Person, mapped to tb_employees)
                      - :Professor (subclass of Employee, mapped to tb_professors)
                      - :Department (mapped to tb_departments)
                    Properties:
                      - :name (data property of Person)
                      - :departmentName (data property of Department)
                      - :worksFor (object prop: Person -> Department)
                      - :headOf (subProperty of worksFor, Employee -> Department)
                    Mappings:
                      - tb_employees (emp_code, emp_name) -> :Employee
                      - tb_professors (prof_code, prof_name, dept_code) -> :Professor
                      - tb_departments (dept_code, dept_name) -> :Department
                      - tb_dept_heads (emp_code, dept_code) -> :headOf
                    Tables: tb_employees, tb_professors, tb_departments, tb_dept_heads
                    """;
        }
        return "No schema available for tenant: " + tenant.getId();
    }

    public List<String> getExampleQueries(String tenantId) {
        if ("university".equals(tenantId)) {
            return List.of(
                    "List all employees",
                    "List all professors",
                    "List all departments",
                    "Who works for Computer Science",
                    "Head of Mathematics"
            );
        }
        if ("sample".equals(tenantId)) {
            return List.of(
                    "List all authors",
                    "List all books",
                    "Who wrote Harry Potter"
            );
        }
        return List.of();
    }
}
