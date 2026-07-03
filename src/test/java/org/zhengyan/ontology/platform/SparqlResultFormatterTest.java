package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.zhengyan.ontology.platform.service.SparqlResultFormat;
import org.zhengyan.ontology.platform.service.SparqlResultFormatter;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author zhengyan
 */
public class SparqlResultFormatterTest {

    private static final String EXAMPLE_URI_1 = "http://example.org/1";
    private static final String UTF_8 = "UTF-8";

    private final SparqlResultFormatter formatter = new SparqlResultFormatter();

    private SparqlQueryResult sampleResult() {
        return new SparqlQueryResult(
                List.of("s", "p", "o"),
                List.of(
                        Map.of("s", EXAMPLE_URI_1, "p", "http://example.org/type", "o", "http://example.org/Thing"),
                        Map.of("s", "http://example.org/2", "p", "http://example.org/label", "o", "\"hello\"")
                ),
                5
        );
    }

    @Test
    void testCsvFormat() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        formatter.writeTupleResult(SparqlResultFormat.CSV, sampleResult(), bos);
        String output = bos.toString(UTF_8);
        assertTrue(output.contains("s"));
        assertTrue(output.contains(EXAMPLE_URI_1));
    }

    @Test
    void testTsvFormat() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        formatter.writeTupleResult(SparqlResultFormat.TSV, sampleResult(), bos);
        String output = bos.toString(UTF_8);
        assertTrue(output.contains("s"));
        assertTrue(output.contains(EXAMPLE_URI_1));
    }

    @Test
    void testSparqlXmlFormat() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        formatter.writeTupleResult(SparqlResultFormat.SPARQL_XML, sampleResult(), bos);
        String output = bos.toString(UTF_8);
        assertTrue(output.contains("<results"));
        assertTrue(output.contains(EXAMPLE_URI_1));
    }

    @Test
    void testSparqlJsonFormat() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        formatter.writeTupleResult(SparqlResultFormat.SPARQL_JSON, sampleResult(), bos);
        String output = bos.toString(UTF_8);
        assertTrue(output.contains("s"));
        assertTrue(output.contains(EXAMPLE_URI_1));
    }

    @Test
    void testFormatFromAcceptHeader() {
        assertTrue(SparqlResultFormat.fromAccept("text/csv").isPresent());
        assertTrue(SparqlResultFormat.fromAccept("text/tab-separated-values").isPresent());
        assertTrue(SparqlResultFormat.fromAccept("application/sparql-results+xml").isPresent());
        assertTrue(SparqlResultFormat.fromAccept("application/sparql-results+json").isPresent());
        assertTrue(SparqlResultFormat.fromAccept(null).isPresent());
        assertTrue(SparqlResultFormat.fromAccept("application/xml").isEmpty());
    }
}
