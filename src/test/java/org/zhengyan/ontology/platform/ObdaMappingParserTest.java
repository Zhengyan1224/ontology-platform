package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.zhengyan.ontology.platform.service.ObdaMappingParser;

import static org.junit.jupiter.api.Assertions.*;

class ObdaMappingParserTest {

    private static final String SOURCE_TABLE = "sourceTable";
    private static final String MAPPING_ID = "mappingId";
    private static final String UNIVERSITY_OBDA = "ontologies/university.obda";

    private final ObdaMappingParser parser = new ObdaMappingParser();

    @Test
    void parseUniversityObda() {
        ObdaMappingParser.ObdaSchema schema = parser.parse(UNIVERSITY_OBDA);

        assertFalse(schema.mappings.isEmpty());
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get(MAPPING_ID).equals("departments")));
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get(MAPPING_ID).equals("employees")));
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get(MAPPING_ID).equals("professors")));
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get(MAPPING_ID).equals("deptHeads")));
    }

    @Test
    void parseUniversitySourceTables() {
        ObdaMappingParser.ObdaSchema schema = parser.parse(UNIVERSITY_OBDA);

        assertTrue(schema.mappings.stream()
                .anyMatch(m -> "tb_departments".equals(m.get(SOURCE_TABLE))));
        assertTrue(schema.mappings.stream()
                .anyMatch(m -> "tb_employees".equals(m.get(SOURCE_TABLE))));
        assertTrue(schema.mappings.stream()
                .anyMatch(m -> "tb_professors".equals(m.get(SOURCE_TABLE))));
        assertTrue(schema.mappings.stream()
                .anyMatch(m -> "tb_dept_heads".equals(m.get(SOURCE_TABLE))));
    }

    @Test
    void parseUniversityPrefixes() {
        ObdaMappingParser.ObdaSchema schema = parser.parse(UNIVERSITY_OBDA);

        assertTrue(schema.prefixes.containsKey(":"));
        assertTrue(schema.prefixes.get(":").contains("example.org/university"));
    }

    @Test
    void parseBooksObda() {
        ObdaMappingParser.ObdaSchema schema = parser.parse("ontologies/exampleBooks.obda");

        assertFalse(schema.mappings.isEmpty());
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get(MAPPING_ID).equals("cl_Authors")));
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get(MAPPING_ID).equals("cl_Books")));
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get(MAPPING_ID).equals("cl_Editions")));
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get(MAPPING_ID).equals("op_writtenBy")));
        assertTrue(schema.mappings.stream().anyMatch(m -> m.get(MAPPING_ID).equals("op_hasEdition")));
    }

    @Test
    void parseMissingFile() {
        ObdaMappingParser.ObdaSchema schema = parser.parse("nonexistent.obda");
        assertTrue(schema.mappings.isEmpty());
        assertTrue(schema.prefixes.isEmpty());
    }
}
