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

/**
 * @author zhengyan
 */
@ExtendWith(MockitoExtension.class)
public class SparqlTemplateGeneratorTest {

    private static final String TENANT_UNIVERSITY = "university";
    private static final String TENANT_SAMPLE = "sample";

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource resource;

    private SparqlTemplateGenerator generator;

    @BeforeEach
    void setUp() {
        given(resourceLoader.getResource(anyString())).willReturn(resource);
        given(resource.exists()).willReturn(false);
        TemplateLoader templateLoader = new TemplateLoader(resourceLoader);
        generator = new SparqlTemplateGenerator(templateLoader);
    }

    @Test
    void testHardcodedEmployees() {
        Optional<String> result = generator.generate(TENANT_UNIVERSITY, "list all employees");
        assertTrue(result.isPresent() && result.get().contains("SELECT ?person ?name") && result.get().contains(":Employee"));
    }

    @Test
    void testHardcodedProfessors() {
        Optional<String> result = generator.generate(TENANT_UNIVERSITY, "list professors");
        assertTrue(result.isPresent() && result.get().contains(":Professor"));
    }

    @Test
    void testHardcodedDepartments() {
        Optional<String> result = generator.generate(TENANT_UNIVERSITY, "List all departments");
        assertTrue(result.isPresent() && result.get().contains(":Department"));
    }

    @Test
    void testHardcodedWhoWorksFor() {
        Optional<String> result = generator.generate(TENANT_UNIVERSITY, "Who works for Computer Science");
        assertTrue(result.isPresent() && result.get().contains(":worksFor") && result.get().contains("Computer Science"));
    }

    @Test
    void testHardcodedHeadOf() {
        Optional<String> result = generator.generate(TENANT_UNIVERSITY, "head of Mathematics");
        assertTrue(result.isPresent() && result.get().contains(":headOf"));
    }

    @Test
    void testHardcodedFindNamed() {
        Optional<String> result = generator.generate(TENANT_UNIVERSITY, "find employee named John");
        assertTrue(result.isPresent() && result.get().contains("\"John\""));
    }

    @Test
    void testHardcodedCount() {
        Optional<String> result = generator.generate(TENANT_UNIVERSITY, "count all people");
        assertTrue(result.isPresent() && result.get().contains("COUNT"));
    }

    @Test
    void testHardcodedBooks() {
        Optional<String> result = generator.generate(TENANT_SAMPLE, "list all books");
        assertTrue(result.isPresent() && result.get().contains(":Book"));
    }

    @Test
    void testHardcodedAuthors() {
        Optional<String> result = generator.generate(TENANT_SAMPLE, "list authors");
        assertTrue(result.isPresent() && result.get().contains(":Author"));
    }

    @Test
    void testHardcodedWhoWrote() {
        Optional<String> result = generator.generate(TENANT_SAMPLE, "Who wrote Harry Potter");
        assertTrue(result.isPresent() && result.get().contains("FILTER(CONTAINS(LCASE(?title)"));
    }

    @Test
    void testHardcodedSearchNamed() {
        Optional<String> result = generator.generate(TENANT_SAMPLE, "find author named J.K. Rowling");
        assertTrue(result.isPresent() && result.get().contains("\"J.K. Rowling\""));
    }

    @Test
    void testHardcodedHowMany() {
        Optional<String> result = generator.generate(TENANT_SAMPLE, "how many authors");
        assertTrue(result.isPresent() && result.get().contains("COUNT"));
    }

    @Test
    void testHardcodedAuthorsFrom() {
        Optional<String> result = generator.generate(TENANT_SAMPLE, "authors from Bloomsbury");
        assertTrue(result.isPresent() && result.get().contains(":AffiliatedWriter"));
    }

    @Test
    void testUnknownTenant() {
        Optional<String> result = generator.generate("unknown", "list all employees");
        assertFalse(result.isPresent());
    }

    @Test
    void testHasTemplatesForKnown() {
        assertTrue(generator.hasTemplatesFor(TENANT_UNIVERSITY));
        assertTrue(generator.hasTemplatesFor(TENANT_SAMPLE));
    }

    @Test
    void testHasTemplatesForUnknown() {
        assertFalse(generator.hasTemplatesFor("unknown"));
    }
}
