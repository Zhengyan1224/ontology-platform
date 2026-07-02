package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.zhengyan.ontology.platform.controller.SparqlController;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.zhengyan.ontology.platform.service.AuditService;
import org.zhengyan.ontology.platform.service.CachedSparqlService;
import org.zhengyan.ontology.platform.service.MetricsService;
import org.zhengyan.ontology.platform.service.SparqlResultFormatter;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SparqlController.class)
@AutoConfigureMockMvc(addFilters = false)
public class SparqlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EngineRegistry engineRegistry;

    @MockitoBean
    private OntologyEngine ontologyEngine;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private MetricsService metricsService;

    @MockitoBean
    private SparqlResultFormatter sparqlResultFormatter;

    @MockitoBean
    private CachedSparqlService cachedSparqlService;

    @Test
    void testExecuteSparql() throws Exception {
        given(engineRegistry.get("test")).willReturn(ontologyEngine);
        given(ontologyEngine.isHealthy()).willReturn(true);
        given(cachedSparqlService.executeQuery(anyString(), anyString()))
                .willReturn(new SparqlQueryResult(
                        List.of("s", "p", "o"),
                        List.of(Map.of("s", "test")),
                        10
                ));
        given(ontologyEngine.translateToSql(anyString())).willReturn("SELECT 1");

        mockMvc.perform(post("/api/v1/tenants/test/sparql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"SELECT ?s WHERE {?s ?p ?o}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variables").isArray());
    }

    @Test
    void testExecuteSparqlDirect() throws Exception {
        given(engineRegistry.get("test")).willReturn(ontologyEngine);
        given(ontologyEngine.isHealthy()).willReturn(true);
        given(cachedSparqlService.executeQuery(anyString(), anyString()))
                .willReturn(new SparqlQueryResult(
                        List.of("s"),
                        List.of(Map.of("s", "test")),
                        5
                ));
        given(ontologyEngine.translateToSql(anyString())).willReturn("SELECT 1");

        mockMvc.perform(post("/api/v1/tenants/test/sparql")
                        .contentType("application/sparql-query")
                        .content("SELECT ?s WHERE {?s ?p ?o}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variables").isArray());
    }
}
