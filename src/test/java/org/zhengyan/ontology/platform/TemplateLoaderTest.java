package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.zhengyan.ontology.platform.service.TemplateLoader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * @author zhengyan
 */
@ExtendWith(MockitoExtension.class)
public class TemplateLoaderTest {

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource resource;

    private TemplateLoader loader;

    @BeforeEach
    void setUp() {
        loader = new TemplateLoader(resourceLoader);
    }

    @Test
    void testLoadSampleYaml() throws Exception {
        String yaml = """
                rules:
                  - patterns:
                      - "list.*(all\\\\s+)?books?"
                    sparql: |
                      PREFIX : <http://example.org#>
                      SELECT ?book ?title WHERE { ?book a :Book . ?book :title ?title . }
                    description: "List all books"
                  - patterns:
                      - "who\\\\s+wrote\\\\s+(.+?)(\\\\?)?$"
                    sparql: |
                      PREFIX : <http://example.org#>
                      SELECT ?author ?name WHERE {
                        ?book :title "{1}" .
                        ?author :name ?name .
                      }
                    params:
                      - group: 1
                    description: "Find who wrote a book"
                """;

        given(resourceLoader.getResource(anyString())).willReturn(resource);
        given(resource.exists()).willReturn(true);
        given(resource.getInputStream()).willReturn(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        Optional<List<TemplateLoader.LoadedRule>> result = loader.load("test-tenant");
        assertTrue(result.isPresent());
        assertEquals(2, result.get().size());
    }

    @Test
    void testPatternMatching() throws Exception {
        String yaml = """
                rules:
                  - patterns:
                      - "list.*authors?"
                    sparql: "SELECT ?a WHERE { ?a a :Author }"
                    description: "List authors"
                """;

        given(resourceLoader.getResource(anyString())).willReturn(resource);
        given(resource.exists()).willReturn(true);
        given(resource.getInputStream()).willReturn(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        Optional<List<TemplateLoader.LoadedRule>> rules = loader.load("test-tenant");
        assertTrue(rules.isPresent());
        assertTrue(rules.get().get(0).matches("list authors"));
        assertTrue(rules.get().get(0).matches("list all authors"));
        assertFalse(rules.get().get(0).matches("books"));
    }

    @Test
    void testParameterReplacement() throws Exception {
        String yaml = """
                rules:
                  - patterns:
                      - "who\\\\s+wrote\\\\s+(.+?)(\\\\?)?$"
                    sparql: |
                      SELECT ?author WHERE { ?book :title "{1}" . }
                    params:
                      - group: 1
                    description: "Find who wrote a book"
                """;

        given(resourceLoader.getResource(anyString())).willReturn(resource);
        given(resource.exists()).willReturn(true);
        given(resource.getInputStream()).willReturn(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        Optional<List<TemplateLoader.LoadedRule>> rules = loader.load("test-tenant");
        assertTrue(rules.isPresent());
        String result = rules.get().get(0).apply("who wrote Harry Potter");
        assertTrue(result.contains("Harry Potter"));
    }

    @Test
    void testNoYamlFileReturnsEmpty() throws Exception {
        given(resourceLoader.getResource(anyString())).willReturn(resource);
        given(resource.exists()).willReturn(false);

        Optional<List<TemplateLoader.LoadedRule>> result = loader.load("nonexistent");
        assertFalse(result.isPresent());
    }

    @Test
    void testCacheReturnsSameInstance() throws Exception {
        String yaml = """
                rules:
                  - patterns:
                      - "list.*authors?"
                    sparql: "SELECT ?a WHERE { ?a a :Author }"
                    description: "List authors"
                """;

        given(resourceLoader.getResource(anyString())).willReturn(resource);
        given(resource.exists()).willReturn(true);
        given(resource.getInputStream()).willReturn(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        Optional<List<TemplateLoader.LoadedRule>> first = loader.load("cached-tenant");
        Optional<List<TemplateLoader.LoadedRule>> second = loader.load("cached-tenant");
        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertSame(first.get(), second.get());
    }

    @Test
    void testHasTemplatesForReturnsFalseForMissing() throws Exception {
        given(resourceLoader.getResource(anyString())).willReturn(resource);
        given(resource.exists()).willReturn(false);

        assertFalse(loader.hasTemplatesFor("missing-tenant"));
    }
}
