package org.zhengyan.ontology.platform.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.model.Tenant;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class OntologyGraphService {

    private static final String LABEL = "label";
    private static final String TYPE_CLASS = "class";
    private static final String TYPE_DATATYPE = "datatype";
    private static final String TYPE_PROPERTY = "property";

    private final TenantConfig tenantConfig;
    private final TenantPersistenceService tenantPersistenceService;
    private final OwlSchemaParser owlParser;
    private final Cache<String, Map<String, Object>> graphCache;

    public OntologyGraphService(TenantConfig tenantConfig,
                                TenantPersistenceService tenantPersistenceService,
                                OwlSchemaParser owlParser,
                                @Value("${ontology.viz.cache-ttl-seconds:300}") int cacheTtlSeconds) {
        this.tenantConfig = tenantConfig;
        this.tenantPersistenceService = tenantPersistenceService;
        this.owlParser = owlParser;
        this.graphCache = Caffeine.newBuilder()
                .maximumSize(50)
                .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
                .build();
    }

    public Map<String, Object> getGraph(String tenantId) {
        Map<String, Object> cached = graphCache.getIfPresent(tenantId);
        if (cached != null) {
            return cached;
        }

        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            throw new IllegalArgumentException("Unknown tenant: " + tenantId);
        }

        OwlSchemaParser.OwlSchema owlSchema = owlParser.parse(tenant.resolveOwlPath());

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        Set<String> nodeIds = new LinkedHashSet<>();

        for (Map<String, Object> cls : owlSchema.classes) {
            String name = (String) cls.get("name");
            addNode(nodes, nodeIds, name, name, TYPE_CLASS);
        }

        for (Map<String, Object> rel : owlSchema.classHierarchy) {
            String child = toLocalName((String) rel.get("child"));
            String parent = toLocalName((String) rel.get("parent"));

            for (String name : List.of(child, parent)) {
                addNode(nodes, nodeIds, name, name, TYPE_CLASS);
            }

            addEdge(edges, child, parent, "subClassOf", "hierarchy", null);
        }

        for (Map<String, Object> prop : owlSchema.properties) {
            String propName = (String) prop.get("name");
            String propType = (String) prop.get("type");
            String domain = toLocalName((String) prop.get("domain"));
            String range = toLocalName((String) prop.get("range"));

            if ("object".equals(propType) && domain != null && range != null) {
                addNode(nodes, nodeIds, domain, domain, TYPE_CLASS);
                addNode(nodes, nodeIds, range, range, TYPE_CLASS);
                addEdge(edges, domain, range, propName, "objectProperty", propType);
            } else if ("datatype".equals(propType) && domain != null && range != null) {
                String datatypeId = "xsd:" + range;
                addNode(nodes, nodeIds, domain, domain, TYPE_CLASS);
                addNode(nodes, nodeIds, datatypeId, datatypeId, TYPE_DATATYPE);
                addEdge(edges, domain, datatypeId, propName, "datatypeProperty", propType);
            } else {
                String propertyTypeNode = "object".equals(propType) ? "ObjectProperty" : "DatatypeProperty";
                addNode(nodes, nodeIds, propName, propName, TYPE_PROPERTY);
                addNode(nodes, nodeIds, propertyTypeNode, propertyTypeNode, TYPE_DATATYPE);
                addEdge(edges, propName, propertyTypeNode, propName, TYPE_PROPERTY, propType);
            }
        }

        for (Map<String, Object> rel : owlSchema.subPropertyOf) {
            String child = toLocalName((String) rel.get("child"));
            String parent = toLocalName((String) rel.get("parent"));
            addNode(nodes, nodeIds, child, child, TYPE_PROPERTY);
            addNode(nodes, nodeIds, parent, parent, TYPE_PROPERTY);
            addEdge(edges, child, parent, "subPropertyOf", "subProperty", null);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);

        graphCache.put(tenantId, result);
        return result;
    }

    private void addNode(List<Map<String, Object>> nodes,
                         Set<String> nodeIds,
                         String id,
                         String label,
                         String type) {
        if (id == null || id.isBlank() || nodeIds.contains(id)) {
            return;
        }
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put(LABEL, label != null ? label : id);
        node.put("type", type);
        nodes.add(node);
        nodeIds.add(id);
    }

    private void addEdge(List<Map<String, Object>> edges,
                         String source,
                         String target,
                         String label,
                         String type,
                         String propertyType) {
        if (source == null || target == null || source.isBlank() || target.isBlank()) {
            return;
        }
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("source", source);
        edge.put("target", target);
        edge.put(LABEL, label);
        edge.put("type", type);
        if (propertyType != null) {
            edge.put("propertyType", propertyType);
        }
        edges.add(edge);
    }

    public void evictCache(String tenantId) {
        graphCache.invalidate(tenantId);
    }

    private String toLocalName(String iri) {
        if (iri == null || iri.isBlank()) {
            return null;
        }
        int hash = iri.lastIndexOf('#');
        if (hash >= 0) return iri.substring(hash + 1);
        int slash = iri.lastIndexOf('/');
        if (slash >= 0) return iri.substring(slash + 1);
        return iri;
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
