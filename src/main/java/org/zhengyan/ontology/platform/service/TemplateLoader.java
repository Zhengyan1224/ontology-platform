package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class TemplateLoader {

    private static final Logger log = LoggerFactory.getLogger(TemplateLoader.class);

    private final ResourceLoader resourceLoader;
    private final Map<String, List<LoadedRule>> templateCache = new ConcurrentHashMap<>();

    public TemplateLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public Optional<List<LoadedRule>> load(String tenantId) {
        if (templateCache.containsKey(tenantId)) {
            return Optional.of(templateCache.get(tenantId));
        }
        List<LoadedRule> rules = loadFromFile(tenantId);
        templateCache.put(tenantId, rules);
        return rules.isEmpty() ? Optional.empty() : Optional.of(rules);
    }

    public boolean hasTemplatesFor(String tenantId) {
        if (templateCache.containsKey(tenantId)) {
            return !templateCache.get(tenantId).isEmpty();
        }
        List<LoadedRule> rules = loadFromFile(tenantId);
        templateCache.put(tenantId, rules);
        return !rules.isEmpty();
    }

    public void clearCache() {
        templateCache.clear();
    }

    @SuppressWarnings("unchecked")
    private List<LoadedRule> loadFromFile(String tenantId) {
        String location = "classpath:nlq-templates/" + tenantId + ".yml";
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                log.info("No YAML template file for tenant '{}' at {}", tenantId, location);
                return List.of();
            }
            try (InputStream is = resource.getInputStream()) {
                Yaml yaml = new Yaml();
                Map<String, Object> root = yaml.load(is);
                if (root == null) return List.of();
                Object rulesObj = root.get("rules");
                if (!(rulesObj instanceof List)) return List.of();
                List<Map<String, Object>> rawRules = (List<Map<String, Object>>) rulesObj;
                List<LoadedRule> rules = rawRules.stream()
                        .map(this::parseRule)
                        .collect(Collectors.toList());
                log.info("Loaded {} template rules for tenant '{}'", rules.size(), tenantId);
                return rules;
            }
        } catch (Exception e) {
            log.warn("Failed to load YAML templates for tenant '{}': {}", tenantId, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private LoadedRule parseRule(Map<String, Object> raw) {
        List<String> patterns;
        Object pObj = raw.get("patterns");
        if (pObj instanceof List) {
            patterns = ((List<Object>) pObj).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        } else {
            patterns = List.of();
        }
        String sparql = raw.getOrDefault("sparql", "").toString();
        Object paramsObj = raw.get("params");
        List<Integer> paramGroups = new ArrayList<>();
        if (paramsObj instanceof List) {
            for (Object p : (List<Object>) paramsObj) {
                if (p instanceof Map) {
                    Object g = ((Map<String, Object>) p).get("group");
                    if (g instanceof Number) {
                        paramGroups.add(((Number) g).intValue());
                    }
                }
            }
        }
        return new LoadedRule(patterns, sparql, paramGroups);
    }

    public static class LoadedRule {
        private final List<Pattern> patterns;
        private final String sparqlTemplate;
        private final List<Integer> paramGroups;

        LoadedRule(List<String> patternStrs, String sparqlTemplate, List<Integer> paramGroups) {
            this.patterns = patternStrs.stream()
                    .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE))
                    .collect(Collectors.toList());
            this.sparqlTemplate = sparqlTemplate;
            this.paramGroups = paramGroups;
        }

        public boolean matches(String question) {
            String normalized = question.toLowerCase().trim();
            return patterns.stream().anyMatch(p -> p.matcher(normalized).find());
        }

        public String apply(String question) {
            String sparql = sparqlTemplate;
            for (int i = 0; i < paramGroups.size(); i++) {
                int group = paramGroups.get(i);
                String value = extractMatch(question, patterns.get(0), group);
                sparql = sparql.replace("{" + (i + 1) + "}", value);
            }
            return sparql;
        }

        private static String extractMatch(String input, Pattern pattern, int group) {
            var m = pattern.matcher(input);
            if (m.find()) {
                String val = m.group(group);
                return val != null ? val.trim() : "";
            }
            return "";
        }
    }
}
