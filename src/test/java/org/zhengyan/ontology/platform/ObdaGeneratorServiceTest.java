package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.zhengyan.ontology.platform.config.OwlGenerationProperties;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.service.JdbcMetadataReader;
import org.zhengyan.ontology.platform.service.ObdaGeneratorService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class ObdaGeneratorServiceTest {

    private String createMemDb(String suffix) {
        return "jdbc:h2:mem:testobda" + suffix + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
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
    void generatesBasicTableMapping() throws Exception {
        String url = createMemDb("basic");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE books (id INT PRIMARY KEY, title VARCHAR(255))");
        }

        ObdaGeneratorService service = new ObdaGeneratorService(
                new OwlGenerationProperties(), new JdbcMetadataReader());
        String obda = service.generateObda(createTenant(url));

        assertNotNull(obda);
        assertTrue(obda.contains("[PrefixDeclaration]"));
        assertTrue(obda.contains("mappingId\tcl_Book"));
        assertTrue(obda.contains(":book/{ID} rdf:type :Book"));
        assertTrue(obda.contains("{TITLE}"));
        assertTrue(obda.contains("SELECT \"ID\", \"TITLE\" FROM \"BOOKS\""));
    }

    @Test
    void foreignKeyGeneratesObjectPropertyMapping() throws Exception {
        String url = createMemDb("fk");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE authors (id INT PRIMARY KEY, name VARCHAR(255))");
            stmt.execute("CREATE TABLE books (id INT PRIMARY KEY, title VARCHAR(255), author_id INT)");
            stmt.execute("ALTER TABLE books ADD FOREIGN KEY (author_id) REFERENCES authors(id)");
        }

        ObdaGeneratorService service = new ObdaGeneratorService(
                new OwlGenerationProperties(), new JdbcMetadataReader());
        String obda = service.generateObda(createTenant(url));

        assertTrue(obda.contains(":authorId :author/{AUTHOR_ID}"));
    }

    @Test
    void joinTableObjectOnlyBehavior() throws Exception {
        String url = createMemDb("join");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE books (id INT PRIMARY KEY)");
            stmt.execute("CREATE TABLE authors (id INT PRIMARY KEY)");
            stmt.execute("CREATE TABLE book_authors (book_id INT, author_id INT, " +
                    "PRIMARY KEY (book_id, author_id))");
            stmt.execute("ALTER TABLE book_authors ADD FOREIGN KEY (book_id) REFERENCES books(id)");
            stmt.execute("ALTER TABLE book_authors ADD FOREIGN KEY (author_id) REFERENCES authors(id)");
        }

        ObdaGeneratorService service = new ObdaGeneratorService(
                new OwlGenerationProperties(), new JdbcMetadataReader());
        String obda = service.generateObda(createTenant(url));

        assertFalse(obda.contains("mappingId\tcl_BookAuthor"), "Join table should not have class mapping");
        assertTrue(obda.contains("mappingId\top_BookAuthor") || obda.contains("mappingId\tcl_"),
                "Join table should have object property mappings");
    }

    @Test
    void customIriTemplate() throws Exception {
        String url = createMemDb("iri");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE employees (id INT PRIMARY KEY, name VARCHAR(255))");
        }

        OwlGenerationProperties props = new OwlGenerationProperties();
        props.setIriTemplate("-{pk}");
        ObdaGeneratorService service = new ObdaGeneratorService(props, new JdbcMetadataReader());
        String obda = service.generateObda(createTenant(url));

        assertTrue(obda.contains(":employee-{ID} rdf:type :Employee"));
    }

    @Test
    void emptyDbGeneratesMinimalObda() throws Exception {
        String url = createMemDb("empty");
        ObdaGeneratorService service = new ObdaGeneratorService(
                new OwlGenerationProperties(), new JdbcMetadataReader());
        String obda = service.generateObda(createTenant(url));

        assertNotNull(obda);
        assertTrue(obda.contains("[PrefixDeclaration]"));
        assertFalse(obda.contains("[MappingDeclaration]"));
    }

    @Test
    void skipJoinTableBehavior() throws Exception {
        String url = createMemDb("skipjoin");
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE books (id INT PRIMARY KEY)");
            stmt.execute("CREATE TABLE authors (id INT PRIMARY KEY)");
            stmt.execute("CREATE TABLE book_authors (book_id INT, author_id INT, " +
                    "PRIMARY KEY (book_id, author_id))");
            stmt.execute("ALTER TABLE book_authors ADD FOREIGN KEY (book_id) REFERENCES books(id)");
            stmt.execute("ALTER TABLE book_authors ADD FOREIGN KEY (author_id) REFERENCES authors(id)");
        }

        OwlGenerationProperties props = new OwlGenerationProperties();
        props.setJoinTableBehavior("skip");
        ObdaGeneratorService service = new ObdaGeneratorService(props, new JdbcMetadataReader());
        String obda = service.generateObda(createTenant(url));

        assertFalse(obda.contains("book_author"), "Join table should be skipped entirely");
        assertTrue(obda.contains("mappingId\tcl_Book"));
        assertTrue(obda.contains("mappingId\tcl_Author"));
    }
}
