package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zhengyan.ontology.platform.service.CachedSparqlService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class SparqlResultLimitTest {

    @Autowired
    private CachedSparqlService cachedSparqlService;

    @Test
    void defaultAppendsLimit() {
        String result = cachedSparqlService.applyMaxResults("SELECT ?s WHERE {?s ?p ?o}");
        assertTrue(result.endsWith(" LIMIT 10000"), "Should append LIMIT 10000: " + result);
    }

    @Test
    void existingLimitNotModified() {
        String sparql = "SELECT ?s WHERE {?s ?p ?o} LIMIT 5";
        String result = cachedSparqlService.applyMaxResults(sparql);
        assertEquals(sparql, result);
    }

    @Test
    void constructQueryNotAffected() {
        String sparql = "CONSTRUCT {?s ?p ?o} WHERE {?s ?p ?o}";
        String result = cachedSparqlService.applyMaxResults(sparql);
        assertEquals(sparql, result);
    }

    @Test
    void askQueryNotAffected() {
        String sparql = "ASK WHERE {?s ?p ?o}";
        String result = cachedSparqlService.applyMaxResults(sparql);
        assertEquals(sparql, result);
    }

    @Test
    void describeQueryNotAffected() {
        String sparql = "DESCRIBE ?s WHERE {?s ?p ?o}";
        String result = cachedSparqlService.applyMaxResults(sparql);
        assertEquals(sparql, result);
    }
}
