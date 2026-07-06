package org.zhengyan.ontology.platform.service;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.observation.annotation.Observed;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;

import java.util.regex.Pattern;

@Service
/**
 * @author 郑炎 Zheng Yan
 */
public class CachedSparqlService {

    private static final Pattern LIMIT_PATTERN = Pattern.compile("(?i)\\bLIMIT\\s+\\d+");

    private final EngineRegistry engineRegistry;
    private final FederatedQueryService federatedQueryService;
    private final CacheManager cacheManager;
    private final int maxResults;

    public CachedSparqlService(EngineRegistry engineRegistry,
                                FederatedQueryService federatedQueryService,
                                CacheManager cacheManager,
                                @Value("${ontology.sparql.max-results:10000}") int maxResults) {
        this.engineRegistry = engineRegistry;
        this.federatedQueryService = federatedQueryService;
        this.cacheManager = cacheManager;
        this.maxResults = maxResults;
    }

    @Observed(name = "sparql.cached.execute")
    @Cacheable(value = "sparqlResults", key = "#tenantId + ':' + #sparql", unless = "#result == null")
    public SparqlQueryResult executeQuery(String tenantId, String sparql) throws Exception {
        if (federatedQueryService.containsServiceClause(sparql)) {
            return federatedQueryService.executeFederated(tenantId, sparql);
        }
        String finalSparql = applyMaxResults(sparql);
        OntologyEngine engine = engineRegistry.get(tenantId);
        if (engine == null) {
            throw new IllegalArgumentException("Tenant not found: " + tenantId);
        }
        return engine.executeQuery(finalSparql);
    }

    @CacheEvict(value = "sparqlResults", allEntries = true)
    public void evictAll() {
    }

    public void evictForTenant(String tenantId) {
        org.springframework.cache.Cache springCache = cacheManager.getCache("sparqlResults");
        if (springCache == null) return;
        if (springCache instanceof CaffeineCache caffeineCache) {
            Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
            String prefix = tenantId + ":";
            nativeCache.asMap().keySet().removeIf(key ->
                    key instanceof String && ((String) key).startsWith(prefix));
        } else {
            springCache.invalidate();
        }
    }

    public String applyMaxResults(String sparql) {
        if (maxResults <= 0) return sparql;
        String trimmed = sparql.strip();
        if (!trimmed.regionMatches(true, 0, "SELECT", 0, 6)) return sparql;
        if (LIMIT_PATTERN.matcher(sparql).find()) return sparql;
        return sparql + " LIMIT " + maxResults;
    }
}
