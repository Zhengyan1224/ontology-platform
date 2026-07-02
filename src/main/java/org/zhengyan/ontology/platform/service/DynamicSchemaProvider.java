package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DynamicSchemaProvider implements SchemaProvider {

    private static final Logger log = LoggerFactory.getLogger(DynamicSchemaProvider.class);

    private final OwlSchemaParser owlParser;
    private final ObdaMappingParser obdaParser;

    private String currentOwlPath;
    private String currentObdaPath;
    private OwlSchemaParser.OwlSchema owlResult;
    private ObdaMappingParser.ObdaSchema obdaResult;

    public DynamicSchemaProvider(OwlSchemaParser owlParser, ObdaMappingParser obdaParser) {
        this.owlParser = owlParser;
        this.obdaParser = obdaParser;
    }

    public void loadFromPaths(String owlPath, String obdaPath) {
        this.currentOwlPath = owlPath;
        this.currentObdaPath = obdaPath;
        this.owlResult = owlParser.parse(owlPath);
        this.obdaResult = obdaParser.parse(obdaPath);
    }

    public String generateSchemaText() {
        StringBuilder sb = new StringBuilder();

        if (obdaResult != null) {
            for (Map.Entry<String, String> prefix : obdaResult.prefixes.entrySet()) {
                sb.append("PREFIX ").append(prefix.getKey()).append(": <").append(prefix.getValue()).append(">\n");
            }
        }

        if (owlResult != null && !owlResult.classes.isEmpty()) {
            sb.append("Classes:\n");
            for (Map<String, Object> cls : owlResult.classes) {
                sb.append("  - :").append(cls.get("name")).append("\n");
            }
        }

        if (owlResult != null && !owlResult.classHierarchy.isEmpty()) {
            sb.append("Class Hierarchy:\n");
            for (Map<String, Object> rel : owlResult.classHierarchy) {
                String child = toLocalName((String) rel.get("child"));
                String parent = toLocalName((String) rel.get("parent"));
                sb.append("  - :").append(child).append(" ⊑ :").append(parent).append("\n");
            }
        }

        if (owlResult != null && !owlResult.properties.isEmpty()) {
            sb.append("Properties:\n");
            for (Map<String, Object> prop : owlResult.properties) {
                sb.append("  - :").append(prop.get("name")).append(" (").append(prop.get("type")).append(")\n");
            }
        }

        if (obdaResult != null && !obdaResult.mappings.isEmpty()) {
            sb.append("Mappings:\n");
            for (Map<String, Object> mapping : obdaResult.mappings) {
                sb.append("  - ").append(mapping.get("mappingId"))
                        .append(" → ").append(mapping.get("sourceTable")).append("\n");
            }
        }

        return sb.toString();
    }

    @Override
    public List<Map<String, Object>> getClasses() {
        if (owlResult == null) return List.of();
        return owlResult.classes;
    }

    @Override
    public List<Map<String, Object>> getClassHierarchy() {
        if (owlResult == null) return List.of();
        return owlResult.classHierarchy;
    }

    @Override
    public List<Map<String, Object>> getProperties() {
        if (owlResult == null) return List.of();
        return owlResult.properties;
    }

    @Override
    public List<Map<String, Object>> getMappings() {
        if (obdaResult == null) return List.of();
        return obdaResult.mappings;
    }

    @Override
    public Map<String, Object> getAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("classes", getClasses());
        result.put("classHierarchy", getClassHierarchy());
        result.put("properties", getProperties());
        result.put("mappings", getMappings());
        return result;
    }

    private String toLocalName(String iri) {
        int hash = iri.lastIndexOf('#');
        if (hash >= 0) return iri.substring(hash + 1);
        int slash = iri.lastIndexOf('/');
        if (slash >= 0) return iri.substring(slash + 1);
        return iri;
    }
}
