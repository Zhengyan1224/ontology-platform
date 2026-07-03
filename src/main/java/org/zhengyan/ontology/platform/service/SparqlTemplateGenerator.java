package org.zhengyan.ontology.platform.service;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class SparqlTemplateGenerator {

    private static final String PREFIX_UNI = "PREFIX : <http://example.org/university#>";
    private static final String PREFIX_BOOKS = "PREFIX : <http://meraka/moss/exampleBooks.owl#>";

    private final TemplateLoader templateLoader;
    private final Map<String, List<Template>> hardcodedTemplates = new LinkedHashMap<>();

    public SparqlTemplateGenerator(TemplateLoader templateLoader) {
        this.templateLoader = templateLoader;
        registerUniversityTemplates();
        registerBooksTemplates();
    }

    public Optional<String> generate(String tenantId, String question) {
        Optional<List<TemplateLoader.LoadedRule>> yamlRules = templateLoader.load(tenantId);
        if (yamlRules.isPresent()) {
            String normalized = question.toLowerCase().trim();
            for (TemplateLoader.LoadedRule rule : yamlRules.get()) {
                if (rule.matches(normalized)) {
                    return Optional.of(rule.apply(question));
                }
            }
            return Optional.empty();
        }

        List<Template> templates = hardcodedTemplates.get(tenantId);
        if (templates == null) return Optional.empty();

        String normalized = question.toLowerCase().trim();
        for (Template template : templates) {
            if (template.pattern.matcher(normalized).find()) {
                return Optional.of(template.generator.apply(question.trim()));
            }
        }
        return Optional.empty();
    }

    public boolean hasTemplatesFor(String tenantId) {
        if (templateLoader.hasTemplatesFor(tenantId)) return true;
        return hardcodedTemplates.containsKey(tenantId) && !hardcodedTemplates.get(tenantId).isEmpty();
    }

    private void registerUniversityTemplates() {
        List<Template> templates = new ArrayList<>();

        templates.add(new Template("list.*(all\\s+)?employees?",
                q -> PREFIX_UNI + "\nSELECT ?person ?name WHERE { ?person a :Employee . ?person :name ?name . }"));

        templates.add(new Template("list.*(all\\s+)?professors?",
                q -> PREFIX_UNI + "\nSELECT ?person ?name WHERE { ?person a :Professor . ?person :name ?name . }"));

        templates.add(new Template("list.*(all\\s+)?departments?",
                q -> PREFIX_UNI + "\nSELECT ?dept ?name WHERE { ?dept a :Department . ?dept :departmentName ?name . }"));

        templates.add(new Template("who\\s+works?\\s+for\\s+(.+?)(\\?)?$",
                q -> {
                    String dept = extractMatch(q, "who\\s+works?\\s+for\\s+(.+?)(\\?)?$");
                    return PREFIX_UNI + "\nSELECT ?person ?name WHERE {\n" +
                            "  ?person :worksFor ?dept .\n" +
                            "  ?dept :departmentName \"" + dept + "\" .\n" +
                            "  ?person :name ?name .\n" +
                            "}";
                }));

        templates.add(new Template("(head|who\\s+heads?)\\s+of?\\s+(.+?)(\\?)?$",
                q -> {
                    String dept = extractMatch(q, "(head|who\\s+heads?)\\s+of?\\s+(.+?)(\\?)?$", 2);
                    return PREFIX_UNI + "\nSELECT ?person ?name WHERE {\n" +
                            "  ?person :headOf ?dept .\n" +
                            "  ?dept :departmentName \"" + dept + "\" .\n" +
                            "  ?person :name ?name .\n" +
                            "}";
                }));

        templates.add(new Template("find\\s+(\\w+\\s+)*named\\s+(.+?)(\\?)?$",
                q -> {
                    String name = extractMatch(q, "find\\s+(\\w+\\s+)*named\\s+(.+?)(\\?)?$", 2);
                    return PREFIX_UNI + "\nSELECT ?person ?name WHERE {\n" +
                            "  ?person :name \"" + name + "\" .\n" +
                            "}";
                }));

        templates.add(new Template("count\\s+(all\\s+)?(employees?|people|persons?)",
                q -> PREFIX_UNI + "\nSELECT (COUNT(?person) AS ?count) WHERE { ?person a :Employee . }"));

        hardcodedTemplates.put("university", templates);
    }

    private void registerBooksTemplates() {
        List<Template> templates = new ArrayList<>();

        templates.add(new Template("list.*(all\\s+)?authors?",
                q -> PREFIX_BOOKS + "\nSELECT ?author ?name WHERE { ?author a :Author . ?author :name ?name . }"));

        templates.add(new Template("list.*(all\\s+)?books?",
                q -> PREFIX_BOOKS + "\nSELECT ?book ?title WHERE { ?book a :Book . ?book :title ?title . }"));

        templates.add(new Template("who\\s+wrote\\s+(.+?)(\\?)?$",
                q -> {
                    String title = extractMatch(q, "who\\s+wrote\\s+(.+?)(\\?)?$");
                    return PREFIX_BOOKS + "\nSELECT ?author ?name WHERE {\n" +
                            "  ?book a :Book .\n" +
                            "  ?book :title ?title .\n" +
                            "  ?book :writtenBy ?author .\n" +
                            "  ?author :name ?name .\n" +
                            "  FILTER(CONTAINS(LCASE(?title), LCASE(\"" + title + "\")))\n" +
                            "}";
                }));

        templates.add(new Template("(find|search)\\s+(\\w+\\s+)*named\\s+(.+?)(\\?)?$",
                q -> {
                    String name = extractMatch(q, "(find|search)\\s+(\\w+\\s+)*named\\s+(.+?)(\\?)?$", 3);
                    return PREFIX_BOOKS + "\nSELECT ?author ?name WHERE {\n" +
                            "  ?author :name \"" + name + "\" .\n" +
                            "}";
                }));

        templates.add(new Template("how\\s+many\\s+(authors?|writers?)",
                q -> PREFIX_BOOKS + "\nSELECT (COUNT(?author) AS ?count) WHERE { ?author a :Author . }"));

        templates.add(new Template("(authors?|writers?)\\s+from\\s+(.+?)(\\?)?$",
                q -> {
                    String org = extractMatch(q, "(authors?|writers?)\\s+from\\s+(.+?)(\\?)?$", 2);
                    return PREFIX_BOOKS + "\nSELECT ?author ?name WHERE {\n" +
                            "  ?author a :AffiliatedWriter .\n" +
                            "  ?author :name ?name .\n" +
                            "  FILTER(CONTAINS(LCASE(?name), LCASE(\"" + org + "\")))\n" +
                            "}";
                }));

        hardcodedTemplates.put("sample", templates);
    }

    private static String extractMatch(String input, String regex) {
        return extractMatch(input, regex, 1);
    }

    private static String extractMatch(String input, String regex, int group) {
        var m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(input);
        if (m.find()) {
            String val = m.group(group);
            return val.trim();
        }
        return "";
    }

    private static class Template {
        final Pattern pattern;
        final UnaryOperator<String> generator;

        Template(String regex, UnaryOperator<String> generator) {
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.generator = generator;
        }
    }
}
