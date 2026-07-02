package org.zhengyan.ontology.platform.service;

import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.model.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class OntologySchemaProvider {

    private static final Logger log = LoggerFactory.getLogger(OntologySchemaProvider.class);

    private final TenantConfig tenantConfig;
    private final TenantPersistenceService tenantPersistenceService;
    private final OwlSchemaParser owlParser;
    private final ObdaMappingParser obdaParser;

    public OntologySchemaProvider(TenantConfig tenantConfig,
                                  TenantPersistenceService tenantPersistenceService,
                                  OwlSchemaParser owlParser,
                                  ObdaMappingParser obdaParser) {
        this.tenantConfig = tenantConfig;
        this.tenantPersistenceService = tenantPersistenceService;
        this.owlParser = owlParser;
        this.obdaParser = obdaParser;
    }

    public Map<String, String> getSchemaDescriptions() {
        Map<String, String> schemas = new LinkedHashMap<>();
        for (Tenant tenant : getAllTenants()) {
            schemas.put(tenant.getId(), describeTenant(tenant));
        }
        return schemas;
    }

    public String getSchemaForTenant(String tenantId) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            throw new IllegalArgumentException("Unknown tenant: " + tenantId);
        }
        return describeTenant(tenant);
    }

    private String describeTenant(Tenant tenant) {
        OwlSchemaParser.OwlSchema owlSchema = owlParser.parse(tenant.resolveOwlPath());
        ObdaMappingParser.ObdaSchema obdaSchema = obdaParser.parse(tenant.resolveObdaPath());

        StringBuilder sb = new StringBuilder();

        if (!obdaSchema.prefixes.isEmpty()) {
            String defaultPrefix = obdaSchema.prefixes.get(":");
            if (defaultPrefix != null) {
                sb.append("PREFIX : <").append(defaultPrefix).append(">\n");
            }
            for (Map.Entry<String, String> entry : obdaSchema.prefixes.entrySet()) {
                if (!":".equals(entry.getKey())) {
                    sb.append("PREFIX ").append(entry.getKey()).append(" <").append(entry.getValue()).append(">\n");
                }
            }
        }
        sb.append("\n");

        if (!owlSchema.classes.isEmpty()) {
            sb.append("Classes:\n");
            Map<String, List<String>> children = buildHierarchy(owlSchema.classHierarchy);
            List<String> roots = findRoots(owlSchema.classHierarchy, owlSchema.classes);
            for (String root : roots) {
                printClassTree(sb, root, children, owlSchema.classes, 0);
            }
            Set<String> hierarchical = new HashSet<>(roots);
            for (Map.Entry<String, List<String>> entry : children.entrySet()) {
                hierarchical.add(entry.getKey());
                for (String c : entry.getValue()) hierarchical.add(c);
            }
            for (Map<String, Object> cls : owlSchema.classes) {
                String name = (String) cls.get("name");
                if (!hierarchical.contains(name)) {
                    sb.append("  - :").append(name).append("\n");
                }
            }
            sb.append("\n");
        }

        if (!owlSchema.properties.isEmpty()) {
            sb.append("Properties:\n");
            for (Map<String, Object> prop : owlSchema.properties) {
                sb.append("  - :").append(prop.get("name"))
                        .append(" (").append(prop.get("type")).append(")\n");
            }
            if (!owlSchema.subPropertyOf.isEmpty()) {
                sb.append("  Sub-properties:\n");
                for (Map<String, Object> rel : owlSchema.subPropertyOf) {
                    sb.append("    - :").append(toLocalName((String) rel.get("child")))
                            .append(" ⊑ :").append(toLocalName((String) rel.get("parent"))).append("\n");
                }
            }
            sb.append("\n");
        }

        if (!obdaSchema.mappings.isEmpty()) {
            sb.append("Database tables:\n");
            for (Map<String, Object> mapping : obdaSchema.mappings) {
                if (mapping.get("sourceTable") != null) {
                    sb.append("  - ").append(mapping.get("sourceTable"));
                    sb.append(" → ").append(mapping.get("mappingId")).append("\n");
                }
            }
        }

        return sb.toString();
    }

    private Map<String, List<String>> buildHierarchy(List<Map<String, Object>> hierarchy) {
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (Map<String, Object> rel : hierarchy) {
            String child = toLocalName((String) rel.get("child"));
            String parent = toLocalName((String) rel.get("parent"));
            children.computeIfAbsent(parent, k -> new ArrayList<>()).add(child);
        }
        return children;
    }

    private List<String> findRoots(List<Map<String, Object>> hierarchy, List<Map<String, Object>> classes) {
        Set<String> allChildren = new HashSet<>();
        Set<String> allParents = new HashSet<>();
        for (Map<String, Object> rel : hierarchy) {
            allChildren.add(toLocalName((String) rel.get("child")));
            allParents.add(toLocalName((String) rel.get("parent")));
        }
        Set<String> classNames = classes.stream()
                .map(c -> (String) c.get("name"))
                .collect(Collectors.toSet());
        List<String> roots = new ArrayList<>();
        for (String name : classNames) {
            if (allParents.contains(name) && !allChildren.contains(name)) {
                roots.add(name);
            }
        }
        if (roots.isEmpty()) {
            roots.addAll(allParents);
        }
        return roots;
    }

    private void printClassTree(StringBuilder sb, String name, Map<String, List<String>> children,
                                List<Map<String, Object>> classes, int depth) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) indent.append("    ");
        sb.append(indent).append("- :").append(name).append("\n");
        List<String> kids = children.get(name);
        if (kids != null) {
            for (String kid : kids) {
                printClassTree(sb, kid, children, classes, depth + 1);
            }
        }
    }

    private String toLocalName(String iri) {
        int hash = iri.lastIndexOf('#');
        if (hash >= 0) return iri.substring(hash + 1);
        int slash = iri.lastIndexOf('/');
        if (slash >= 0) return iri.substring(slash + 1);
        return iri;
    }

    public List<String> getExampleQueries(String tenantId) {
        if ("university".equals(tenantId)) {
            return List.of(
                    "List all employees",
                    "List all professors",
                    "List all departments",
                    "Who works for Computer Science",
                    "Head of Mathematics"
            );
        }
        if ("sample".equals(tenantId)) {
            return List.of(
                    "List all authors",
                    "List all books",
                    "Who wrote Harry Potter"
            );
        }
        return List.of();
    }

    private List<Tenant> getAllTenants() {
        List<Tenant> all = new ArrayList<>(tenantConfig.getTenants());
        for (Tenant persisted : tenantPersistenceService.findAll()) {
            if (all.stream().noneMatch(t -> t.getId().equals(persisted.getId()))) {
                all.add(persisted);
            }
        }
        return all;
    }

    private Tenant findTenant(String tenantId) {
        return getAllTenants().stream()
                .filter(t -> t.getId().equals(tenantId))
                .findFirst()
                .orElse(null);
    }
}
