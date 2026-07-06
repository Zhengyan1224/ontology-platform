package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.zhengyan.ontology.platform.config.OwlGenerationProperties;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.service.JdbcMetadataReader;
import org.zhengyan.ontology.platform.service.OwlGeneratorService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class OwlGeneratorServiceTest {

    private String createMemDb(String suffix) {
        return "jdbc:h2:mem:testowl" + suffix + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
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
    void generateOwlProducesValidTurtle() throws Exception {
        String url = createMemDb("basic");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE books (id INT PRIMARY KEY, title VARCHAR(255), author_id INT)");
            stmt.execute("CREATE TABLE authors (id INT PRIMARY KEY, name VARCHAR(255))");
            stmt.execute("ALTER TABLE books ADD FOREIGN KEY (author_id) REFERENCES authors(id)");
        }

        OwlGeneratorService service = new OwlGeneratorService(new OwlGenerationProperties(), new JdbcMetadataReader());
        String owl = service.generateOwl(createTenant(url));

        assertNotNull(owl);
        assertTrue(owl.contains("@prefix owl:"));
        assertTrue(owl.contains(":Book rdf:type owl:Class"));
        assertTrue(owl.contains(":Author rdf:type owl:Class"));
        assertTrue(owl.contains(":title rdf:type owl:DatatypeProperty"));
        assertTrue(owl.contains(":authorId rdf:type owl:ObjectProperty"));
    }

    @Test
    void singularizesTableNames() throws Exception {
        String url = createMemDb("singular");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE books (id INT PRIMARY KEY)");
            stmt.execute("CREATE TABLE categories (id INT PRIMARY KEY)");
            stmt.execute("CREATE TABLE statuses (id INT PRIMARY KEY)");
        }

        OwlGeneratorService service = new OwlGeneratorService(new OwlGenerationProperties(), new JdbcMetadataReader());
        String owl = service.generateOwl(createTenant(url));

        assertTrue(owl.contains(":Book rdf:type owl:Class"));
        assertTrue(owl.contains(":Category rdf:type owl:Class"));
        assertTrue(owl.contains(":Status rdf:type owl:Class"));
    }

    @Test
    void foreignKeysGenerateObjectProperties() throws Exception {
        String url = createMemDb("fk");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE departments (id INT PRIMARY KEY, name VARCHAR(255))");
            stmt.execute("CREATE TABLE employees (id INT PRIMARY KEY, name VARCHAR(255), department_id INT)");
            stmt.execute("ALTER TABLE employees ADD FOREIGN KEY (department_id) REFERENCES departments(id)");
        }

        OwlGeneratorService service = new OwlGeneratorService(new OwlGenerationProperties(), new JdbcMetadataReader());
        String owl = service.generateOwl(createTenant(url));

        assertTrue(owl.contains(":departmentId rdf:type owl:ObjectProperty"));
        assertTrue(owl.contains("rdfs:domain :Employee"));
        assertTrue(owl.contains("rdfs:range :Department"));
    }

    @Test
    void appliesPascalCaseNaming() throws Exception {
        String url = createMemDb("pascal");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE user_profiles (id INT PRIMARY KEY, full_name VARCHAR(255))");
        }

        OwlGeneratorService service = new OwlGeneratorService(new OwlGenerationProperties(), new JdbcMetadataReader());
        String owl = service.generateOwl(createTenant(url));

        assertTrue(owl.contains(":UserProfile rdf:type owl:Class"));
        assertTrue(owl.contains(":fullname rdf:type owl:DatatypeProperty"));
    }

    @Test
    void camelCaseMode() throws Exception {
        String url = createMemDb("camel");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE book_entries (id INT PRIMARY KEY, entry_title VARCHAR(255))");
        }

        OwlGenerationProperties props = new OwlGenerationProperties();
        props.setNameCase("camelCase");
        OwlGeneratorService service = new OwlGeneratorService(props, new JdbcMetadataReader());
        String owl = service.generateOwl(createTenant(url));

        assertTrue(owl.contains(":bookEntry rdf:type owl:Class"));
    }

    @Test
    void customPrefixes() throws Exception {
        String url = createMemDb("prefix");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE books (id INT PRIMARY KEY, title VARCHAR(255))");
        }

        OwlGenerationProperties props = new OwlGenerationProperties();
        props.setTableToClassPrefix("ex_");
        props.setColumnToPropertyPrefix("has");
        OwlGeneratorService service = new OwlGeneratorService(props, new JdbcMetadataReader());
        String owl = service.generateOwl(createTenant(url));

        assertTrue(owl.contains(":ExBook rdf:type owl:Class"));
        assertTrue(owl.contains(":hasTitle rdf:type owl:DatatypeProperty"));
    }

    @Test
    void primaryKeyMarkedFunctionalProperty() throws Exception {
        String url = createMemDb("pkfunc");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE products (id INT PRIMARY KEY, sku VARCHAR(50), name VARCHAR(255))");
        }

        OwlGeneratorService service = new OwlGeneratorService(new OwlGenerationProperties(), new JdbcMetadataReader());
        String owl = service.generateOwl(createTenant(url));

        assertTrue(owl.contains(":id rdf:type owl:FunctionalProperty"));
    }

    @Test
    void emptyDatabaseGeneratesOnlyOntologyHeader() throws Exception {
        String url = createMemDb("empty");
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
        }

        OwlGeneratorService service = new OwlGeneratorService(new OwlGenerationProperties(), new JdbcMetadataReader());
        String owl = service.generateOwl(createTenant(url));

        assertNotNull(owl);
        assertTrue(owl.contains("@prefix owl:"));
        assertTrue(owl.contains("rdf:type owl:Ontology"));
        assertFalse(owl.contains("rdf:type owl:Class"));
    }

    @Test
    void singularizeEdgeCases() throws Exception {
        String url = createMemDb("edge");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE addresses (id INT PRIMARY KEY)");
            stmt.execute("CREATE TABLE series (id INT PRIMARY KEY)");
            stmt.execute("CREATE TABLE species (id INT PRIMARY KEY)");
        }

        OwlGeneratorService service = new OwlGeneratorService(new OwlGenerationProperties(), new JdbcMetadataReader());
        String owl = service.generateOwl(createTenant(url));

        assertTrue(owl.contains(":Address rdf:type owl:Class"));
        assertTrue(owl.contains(":Sery rdf:type owl:Class"));
        assertTrue(owl.contains(":Specy rdf:type owl:Class"));
    }
}
