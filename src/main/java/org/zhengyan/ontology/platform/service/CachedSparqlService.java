package org.zhengyan.ontology.platform.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;

@Service
public class CachedSparqlService {

    private final EngineRegistry engineRegistry;
    private final FederatedQueryService federatedQueryService;

    public CachedSparqlService(EngineRegistry engineRegistry, FederatedQueryService federatedQueryService) {
        this.engineRegistry = engineRegistry;
        this.federatedQueryService = federatedQueryService;
    }

    @Cacheable(value = "sparqlResults", key = "#tenantId + ':' + #sparql", unless = "#result == null")
    public SparqlQueryResult executeQuery(String tenantId, String sparql) throws Exception {
        if (federatedQueryService.containsServiceClause(sparql)) {
            return federatedQueryService.executeFederated(tenantId, sparql);
        }
        OntologyEngine engine = engineRegistry.get(tenantId);
        if (engine == null) {
            throw new IllegalArgumentException("Tenant not found: " + tenantId);
        }
        return engine.executeQuery(sparql);
    }

    @CacheEvict(value = "sparqlResults", allEntries = true)
    public void evictAll() {
    }

    public void evictForTenant(String tenantId) {
        evictAll();
    }
}
