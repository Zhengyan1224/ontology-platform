package org.zhengyan.ontology.platform.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.model.Tenant;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OntologyGraphService {

    private static final String LABEL = "label";

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
            if (!nodeIds.contains(name)) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", name);
                node.put(LABEL, name);
                node.put("type", "class");
                nodes.add(node);
                nodeIds.add(name);
            }
        }

        for (Map<String, Object> rel : owlSchema.classHierarchy) {
            String child = toLocalName((String) rel.get("child"));
            String parent = toLocalName((String) rel.get("parent"));

            for (String name : List.of(child, parent)) {
                if (!nodeIds.contains(name)) {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", name);
                    node.put(LABEL, name);
                    node.put("type", "class");
                    nodes.add(node);
                    nodeIds.add(name);
                }
            }

            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", child);
            edge.put("target", parent);
            edge.put(LABEL, "subClassOf");
            edge.put("type", "hierarchy");
            edges.add(edge);
        }

        for (Map<String, Object> prop : owlSchema.properties) {
            String propName = (String) prop.get("name");
            String propType = (String) prop.get("type");

            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", propName);
            edge.put("target", propType.equals("object") ? "object" : "datatype");
            edge.put(LABEL, propName);
            edge.put("type", "property");
            edge.put("propertyType", propType);
            edges.add(edge);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);

        graphCache.put(tenantId, result);
        return result;
    }

    public void evictCache(String tenantId) {
        graphCache.invalidate(tenantId);
    }

    private String toLocalName(String iri) {
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
