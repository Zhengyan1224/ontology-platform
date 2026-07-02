package org.zhengyan.ontology.platform.service;

import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.model.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        sb.append("Tenant: ").append(tenant.getId()).append("\n");

        if (!obdaSchema.prefixes.isEmpty()) {
            String defaultPrefix = obdaSchema.prefixes.get(":");
            if (defaultPrefix != null) {
                sb.append("PREFIX: <").append(defaultPrefix).append(">\n");
            }
        }

        if (!owlSchema.classes.isEmpty()) {
            sb.append("Classes:\n");
            for (Map<String, Object> cls : owlSchema.classes) {
                sb.append("  - :").append(cls.get("name")).append("\n");
            }
        }

        if (!owlSchema.classHierarchy.isEmpty()) {
            sb.append("Class Hierarchy:\n");
            for (Map<String, Object> rel : owlSchema.classHierarchy) {
                sb.append("  - :").append(toLocalName((String) rel.get("child")))
                        .append(" ⊑ :").append(toLocalName((String) rel.get("parent"))).append("\n");
            }
        }

        if (!owlSchema.properties.isEmpty()) {
            sb.append("Properties:\n");
            for (Map<String, Object> prop : owlSchema.properties) {
                sb.append("  - :").append(prop.get("name"))
                        .append(" (").append(prop.get("type")).append(")\n");
            }
        }

        if (!obdaSchema.mappings.isEmpty()) {
            sb.append("Mappings:\n");
            for (Map<String, Object> mapping : obdaSchema.mappings) {
                sb.append("  - ").append(mapping.get("mappingId"));
                if (mapping.get("sourceTable") != null) {
                    sb.append(" → ").append(mapping.get("sourceTable"));
                }
                sb.append("\n");
            }
        }

        return sb.toString();
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
