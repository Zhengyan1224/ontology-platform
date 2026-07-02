package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.zhengyan.ontology.platform.service.ObdaMappingParser;

import static org.junit.jupiter.api.Assertions.*;

class ObdaMappingParserTest {

    private final ObdaMappingParser parser = new ObdaMappingParser();

    @Test
    void parseUniversityObda() {
        ObdaMappingParser.ObdaSchema schema = parser.parse("ontologies/university.obda");

        assertFalse(schema.mappings.isEmpty());
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get("mappingId").equals("departments")));
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get("mappingId").equals("employees")));
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get("mappingId").equals("professors")));
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get("mappingId").equals("deptHeads")));
    }

    @Test
    void parseUniversitySourceTables() {
        ObdaMappingParser.ObdaSchema schema = parser.parse("ontologies/university.obda");

        assertTrue(schema.mappings.stream()
                .anyMatch(m -> "tb_departments".equals(m.get("sourceTable"))));
        assertTrue(schema.mappings.stream()
                .anyMatch(m -> "tb_employees".equals(m.get("sourceTable"))));
        assertTrue(schema.mappings.stream()
                .anyMatch(m -> "tb_professors".equals(m.get("sourceTable"))));
        assertTrue(schema.mappings.stream()
                .anyMatch(m -> "tb_dept_heads".equals(m.get("sourceTable"))));
    }

    @Test
    void parseUniversityPrefixes() {
        ObdaMappingParser.ObdaSchema schema = parser.parse("ontologies/university.obda");

        assertTrue(schema.prefixes.containsKey(":"));
        assertTrue(schema.prefixes.get(":").contains("example.org/university"));
    }

    @Test
    void parseBooksObda() {
        ObdaMappingParser.ObdaSchema schema = parser.parse("ontologies/exampleBooks.obda");

        assertFalse(schema.mappings.isEmpty());
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get("mappingId").equals("cl_Authors")));
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get("mappingId").equals("cl_Books")));
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get("mappingId").equals("cl_Editions")));
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get("mappingId").equals("op_writtenBy")));
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get("mappingId").equals("op_hasEdition")));
    }

    @Test
    void parseMissingFile() {
        ObdaMappingParser.ObdaSchema schema = parser.parse("nonexistent.obda");
        assertTrue(schema.mappings.isEmpty());
        assertTrue(schema.prefixes.isEmpty());
    }
}
