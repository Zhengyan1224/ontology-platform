package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.service.ObdaMappingValidator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class ObdaMappingValidatorTest {

    private String createMemDb(String suffix) {
        return "jdbc:h2:mem:val" + suffix + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
    }

    private Tenant createTenant(String url) {
        Tenant tenant = new Tenant();
        tenant.setId("test");
        tenant.setJdbcUrl(url);
        tenant.setJdbcUsername("sa");
        tenant.setJdbcPassword("");
        tenant.setJdbcDriver("org.h2.Driver");
        return tenant;
    }

    @Test
    void validMappingReturnsSuccess() {
        String url = createMemDb("valid");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE \"BOOKS\" (\"ID\" INT PRIMARY KEY, \"TITLE\" VARCHAR(255))");
        } catch (Exception e) {
            fail("Setup failed: " + e.getMessage());
        }

        Tenant tenant = createTenant(url);
        String obda = "[PrefixDeclaration]\n" +
                ":\thttp://example.org#\n\n" +
                "[MappingDeclaration] @collection [[\n" +
                "mappingId\tcl_Books\n" +
                "target\t\t:book/{ID} a :Book ; :title {TITLE} .\n" +
                "source\t\tSELECT \"ID\", \"TITLE\" FROM \"BOOKS\"\n" +
                "]]";

        ObdaMappingValidator validator = new ObdaMappingValidator();
        ObdaMappingValidator.ValidationResult result = validator.validate(tenant, obda);
        assertTrue(result.valid(), "Expected valid mapping, got errors: " + result.errors());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void invalidTableReturnsError() {
        String url = createMemDb("invalidTable");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE \"BOOKS\" (\"ID\" INT PRIMARY KEY, \"TITLE\" VARCHAR(255))");
        } catch (Exception e) {
            fail("Setup failed: " + e.getMessage());
        }

        Tenant tenant = createTenant(url);
        String obda = "[MappingDeclaration] @collection [[\n" +
                "mappingId\tcl_Books\n" +
                "target\t\t:book/{ID} a :Book .\n" +
                "source\t\tSELECT \"ID\" FROM \"NONEXISTENT_TABLE\"\n" +
                "]]";

        ObdaMappingValidator validator = new ObdaMappingValidator();
        ObdaMappingValidator.ValidationResult result = validator.validate(tenant, obda);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("NONEXISTENT_TABLE")),
                "Expected error about missing table, got: " + result.errors());
    }

    @Test
    void invalidColumnReturnsError() {
        String url = createMemDb("invalidCol");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE \"BOOKS\" (\"ID\" INT PRIMARY KEY, \"TITLE\" VARCHAR(255))");
        } catch (Exception e) {
            fail("Setup failed: " + e.getMessage());
        }

        Tenant tenant = createTenant(url);
        String obda = "[MappingDeclaration] @collection [[\n" +
                "mappingId\tcl_Books\n" +
                "target\t\t:book/{ID} a :Book ; :title {TITLE} .\n" +
                "source\t\tSELECT \"ID\", \"NONEXISTENT_COL\" FROM \"BOOKS\"\n" +
                "]]";

        ObdaMappingValidator validator = new ObdaMappingValidator();
        ObdaMappingValidator.ValidationResult result = validator.validate(tenant, obda);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("NONEXISTENT_COL")),
                "Expected error about missing column, got: " + result.errors());
    }

    @Test
    void emptyContentReturnsError() {
        String url = createMemDb("empty");
        Tenant tenant = createTenant(url);
        ObdaMappingValidator validator = new ObdaMappingValidator();
        ObdaMappingValidator.ValidationResult result = validator.validate(tenant, "");
        assertFalse(result.valid());
    }
}
