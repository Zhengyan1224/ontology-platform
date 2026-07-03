package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.zhengyan.ontology.platform.service.*;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class GraphQlEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EngineRegistry engineRegistry;

    @MockitoBean
    private CachedSparqlService cachedSparqlService;

    @MockitoBean
    private NaturalLanguageQueryService naturalLanguageQueryService;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private MetricsService metricsService;

    @Test
    void testSparqlQuery() throws Exception {
        OntologyEngine mockEngine = org.mockito.Mockito.mock(OntologyEngine.class);
        given(engineRegistry.get("test")).willReturn(mockEngine);
        given(mockEngine.executeQuery(anyString())).willReturn(new SparqlQueryResult(
                List.of("s"),
                List.of(Map.of("s", "test")),
                10
        ));

        mockMvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"{ sparql(tenantId: \\\"test\\\", query: \\\"SELECT ?s WHERE {?s ?p ?o}\\\") { variables executionTimeMs } }\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sparql.variables").isArray())
                .andExpect(jsonPath("$.data.sparql.executionTimeMs").value(10));
    }

    @Test
    void testNlqQuery() throws Exception {
        given(naturalLanguageQueryService.answer("test", "list all books"))
                .willReturn(new NlqResult("list all books",
                        "SELECT ?s WHERE {?s a <http://meraka/moss/exampleBooks.owl#Book>}",
                        "template",
                        List.of("s"),
                        List.of(Map.of("s", "http://example.org/book1")),
                        15));

        mockMvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"{ nlq(tenantId: \\\"test\\\", question: \\\"list all books\\\") { question sparql mode variables executionTimeMs } }\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nlq.question").value("list all books"))
                .andExpect(jsonPath("$.data.nlq.mode").value("template"))
                .andExpect(jsonPath("$.data.nlq.variables").isArray())
                .andExpect(jsonPath("$.data.nlq.executionTimeMs").value(15));
    }
}
