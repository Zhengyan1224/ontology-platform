package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ResourceLoader;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.zhengyan.ontology.platform.service.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class NaturalLanguageQueryServiceTest {

    @Mock
    private EngineRegistry engineRegistry;

    @Mock
    private SparqlTemplateGenerator templateGenerator;

    @Mock
    private OntologySchemaProvider schemaProvider;

    @Mock
    private OntologyEngine ontologyEngine;

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private SessionManager sessionManager;

    private NaturalLanguageQueryService nlqService;

    @Test
    void testQueryViaTemplates() throws Exception {
        given(templateGenerator.generate("test", "List all classes"))
                .willReturn(Optional.of("SELECT ?class WHERE { ?class a owl:Class }"));
        given(engineRegistry.get("test")).willReturn(ontologyEngine);
        given(ontologyEngine.executeQuery(anyString()))
                .willReturn(new SparqlQueryResult(List.of("class"), List.of(), 5));

        nlqService = new NaturalLanguageQueryService(
                engineRegistry, templateGenerator, schemaProvider,
                resourceLoader, sessionManager,
                "", "gpt-4o-mini", "");

        NlqResult result = nlqService.answer("test", "List all classes");
        assertNotNull(result);
        assertEquals("template", result.getMode());
        assertEquals(1, result.getVariables().size());
    }

    @Test
    @SuppressWarnings("java:S100")
    void testQueryViaTemplates_FallbackEnsuresSparql() {
        given(templateGenerator.generate(anyString(), anyString()))
                .willReturn(Optional.empty());

        nlqService = new NaturalLanguageQueryService(
                engineRegistry, templateGenerator, schemaProvider,
                resourceLoader, sessionManager,
                "", "gpt-4o-mini", "");

        assertThrows(IllegalArgumentException.class,
                () -> nlqService.answer("test", "unknown query"));
    }
}
