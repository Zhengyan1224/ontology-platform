package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.zhengyan.ontology.platform.service.OwlSchemaParser;

import static org.junit.jupiter.api.Assertions.*;

class OwlSchemaParserTest {

    private final OwlSchemaParser parser = new OwlSchemaParser();

    @Test
    void parseUniversityOwl() {
        OwlSchemaParser.OwlSchema schema = parser.parse("ontologies/university.owl");

        assertFalse(schema.classes.isEmpty());
        assertTrue(schema.classes.stream().anyMatch(c -> c.get("name").equals("Person")));
        assertTrue(schema.classes.stream().anyMatch(c -> c.get("name").equals("Employee")));
        assertTrue(schema.classes.stream().anyMatch(c -> c.get("name").equals("Professor")));
        assertTrue(schema.classes.stream().anyMatch(c -> c.get("name").equals("Department")));
    }

    @Test
    void parseUniversityClassHierarchy() {
        OwlSchemaParser.OwlSchema schema = parser.parse("ontologies/university.owl");

        assertFalse(schema.classHierarchy.isEmpty());
        assertTrue(schema.classHierarchy.stream()
                .anyMatch(h -> h.get("child").toString().endsWith("#Professor")
                        && h.get("parent").toString().endsWith("#Employee")));
        assertTrue(schema.classHierarchy.stream()
                .anyMatch(h -> h.get("child").toString().endsWith("#Employee")
                        && h.get("parent").toString().endsWith("#Person")));
    }

    @Test
    void parseUniversityProperties() {
        OwlSchemaParser.OwlSchema schema = parser.parse("ontologies/university.owl");

        assertTrue(schema.properties.stream()
                .anyMatch(p -> p.get("name").equals("worksFor") && p.get("type").equals("object")));
        assertTrue(schema.properties.stream()
                .anyMatch(p -> p.get("name").equals("headOf") && p.get("type").equals("object")));
        assertTrue(schema.properties.stream()
                .anyMatch(p -> p.get("name").equals("name") && p.get("type").equals("datatype")));
        assertTrue(schema.properties.stream()
                .anyMatch(p -> p.get("name").equals("departmentName") && p.get("type").equals("datatype")));
    }

    @Test
    void parseUniversitySubPropertyOf() {
        OwlSchemaParser.OwlSchema schema = parser.parse("ontologies/university.owl");

        assertFalse(schema.subPropertyOf.isEmpty());
        assertTrue(schema.subPropertyOf.stream()
                .anyMatch(sp -> sp.get("child").toString().endsWith("#headOf")
                        && sp.get("parent").toString().endsWith("#worksFor")));
    }

    @Test
    void parseBooksOwl() {
        OwlSchemaParser.OwlSchema schema = parser.parse("ontologies/exampleBooks.owl");

        assertFalse(schema.classes.isEmpty());
        assertTrue(schema.classes.stream().anyMatch(c -> c.get("name").equals("Author")));
        assertTrue(schema.classes.stream().anyMatch(c -> c.get("name").equals("Book")));
        assertTrue(schema.classes.stream().anyMatch(c -> c.get("name").equals("Edition")));
    }

    @Test
    void parseMissingFile() {
        OwlSchemaParser.OwlSchema schema = parser.parse("nonexistent.owl");
        assertTrue(schema.classes.isEmpty());
        assertTrue(schema.properties.isEmpty());
    }
}
