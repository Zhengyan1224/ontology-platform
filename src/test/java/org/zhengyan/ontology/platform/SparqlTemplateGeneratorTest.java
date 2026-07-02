package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.zhengyan.ontology.platform.service.SparqlTemplateGenerator;
import org.zhengyan.ontology.platform.service.TemplateLoader;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class SparqlTemplateGeneratorTest {

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource resource;

    private TemplateLoader templateLoader;
    private SparqlTemplateGenerator generator;

    @BeforeEach
    void setUp() {
        given(resourceLoader.getResource(anyString())).willReturn(resource);
        given(resource.exists()).willReturn(false);
        templateLoader = new TemplateLoader(resourceLoader);
        generator = new SparqlTemplateGenerator(templateLoader);
    }

    @Test
    void testHardcodedEmployees() {
        Optional<String> result = generator.generate("university", "list all employees");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("SELECT ?person ?name"));
        assertTrue(result.get().contains(":Employee"));
    }

    @Test
    void testHardcodedProfessors() {
        Optional<String> result = generator.generate("university", "list professors");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains(":Professor"));
    }

    @Test
    void testHardcodedDepartments() {
        Optional<String> result = generator.generate("university", "List all departments");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains(":Department"));
    }

    @Test
    void testHardcodedWhoWorksFor() {
        Optional<String> result = generator.generate("university", "Who works for Computer Science");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains(":worksFor"));
        assertTrue(result.get().contains("Computer Science"));
    }

    @Test
    void testHardcodedHeadOf() {
        Optional<String> result = generator.generate("university", "head of Mathematics");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains(":headOf"));
    }

    @Test
    void testHardcodedFindNamed() {
        Optional<String> result = generator.generate("university", "find employee named John");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("\"John\""));
    }

    @Test
    void testHardcodedCount() {
        Optional<String> result = generator.generate("university", "count all people");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("COUNT"));
    }

    @Test
    void testHardcodedBooks() {
        Optional<String> result = generator.generate("sample", "list all books");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains(":Book"));
    }

    @Test
    void testHardcodedAuthors() {
        Optional<String> result = generator.generate("sample", "list authors");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains(":Author"));
    }

    @Test
    void testHardcodedWhoWrote() {
        Optional<String> result = generator.generate("sample", "Who wrote Harry Potter");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("FILTER(CONTAINS(LCASE(?title)"));
    }

    @Test
    void testHardcodedSearchNamed() {
        Optional<String> result = generator.generate("sample", "find author named J.K. Rowling");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("\"J.K. Rowling\""));
    }

    @Test
    void testHardcodedHowMany() {
        Optional<String> result = generator.generate("sample", "how many authors");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("COUNT"));
    }

    @Test
    void testHardcodedAuthorsFrom() {
        Optional<String> result = generator.generate("sample", "authors from Bloomsbury");
        assertTrue(result.isPresent());
        assertTrue(result.get().contains(":AffiliatedWriter"));
    }

    @Test
    void testUnknownTenant() {
        Optional<String> result = generator.generate("unknown", "list all employees");
        assertFalse(result.isPresent());
    }

    @Test
    void testHasTemplatesForKnown() {
        assertTrue(generator.hasTemplatesFor("university"));
        assertTrue(generator.hasTemplatesFor("sample"));
    }

    @Test
    void testHasTemplatesForUnknown() {
        assertFalse(generator.hasTemplatesFor("unknown"));
    }
}
