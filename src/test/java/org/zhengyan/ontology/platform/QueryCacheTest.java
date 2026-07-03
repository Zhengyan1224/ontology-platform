package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.zhengyan.ontology.platform.service.CachedSparqlService;
import org.zhengyan.ontology.platform.service.FederatedQueryService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = QueryCacheTest.TestConfig.class)
public class QueryCacheTest {

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("sparqlResults");
        }

        @Bean
        public EngineRegistry engineRegistry() {
            return mock(EngineRegistry.class);
        }

        @Bean
        public FederatedQueryService federatedQueryService() {
            return mock(FederatedQueryService.class);
        }

        @Bean
        public CachedSparqlService cachedSparqlService(EngineRegistry er, FederatedQueryService fqs) {
            return new CachedSparqlService(er, fqs);
        }
    }

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CachedSparqlService cachedSparqlService;

    @Autowired
    private EngineRegistry engineRegistry;

    @Autowired
    private FederatedQueryService federatedQueryService;

    @BeforeEach
    void clearCache() {
        cacheManager.getCache("sparqlResults").clear();
    }

    @Test
    void cacheHit() throws Exception {
        OntologyEngine engine = mock(OntologyEngine.class);
        SparqlQueryResult result = new SparqlQueryResult(List.of("s"), List.of(Map.of("s", "v1")), 1);
        String query = "SELECT ?s WHERE {?s ?p ?o} cacheHit";

        given(engineRegistry.get("t-hit")).willReturn(engine);
        given(engine.executeQuery(query)).willReturn(result);
        given(federatedQueryService.containsServiceClause(anyString())).willReturn(false);

        SparqlQueryResult r1 = cachedSparqlService.executeQuery("t-hit", query);
        SparqlQueryResult r2 = cachedSparqlService.executeQuery("t-hit", query);

        assertNotNull(r1);
        assertEquals(r1.getVariables(), r2.getVariables());
        assertEquals(r1.getResults(), r2.getResults());
        verify(engine, times(1)).executeQuery(anyString());
    }

    @Test
    void cacheMiss() throws Exception {
        OntologyEngine engine = mock(OntologyEngine.class);
        SparqlQueryResult result1 = new SparqlQueryResult(List.of("s"), List.of(Map.of("s", "v1")), 1);
        SparqlQueryResult result2 = new SparqlQueryResult(List.of("s"), List.of(Map.of("s", "v2")), 1);

        given(engineRegistry.get("t-miss")).willReturn(engine);
        given(engine.executeQuery("SELECT ?s WHERE {?s ?p ?o} cacheMiss1")).willReturn(result1);
        given(engine.executeQuery("SELECT ?s WHERE {?s ?p ?o} cacheMiss2")).willReturn(result2);
        given(federatedQueryService.containsServiceClause(anyString())).willReturn(false);

        cachedSparqlService.executeQuery("t-miss", "SELECT ?s WHERE {?s ?p ?o} cacheMiss1");
        cachedSparqlService.executeQuery("t-miss", "SELECT ?s WHERE {?s ?p ?o} cacheMiss2");

        verify(engine, times(2)).executeQuery(anyString());
    }

    @Test
    void evictAll() throws Exception {
        OntologyEngine engine = mock(OntologyEngine.class);
        SparqlQueryResult result = new SparqlQueryResult(List.of("s"), List.of(Map.of("s", "v1")), 1);
        String query = "SELECT ?s WHERE {?s ?p ?o} evictAll";

        given(engineRegistry.get("t-evict")).willReturn(engine);
        given(engine.executeQuery(anyString())).willReturn(result);
        given(federatedQueryService.containsServiceClause(anyString())).willReturn(false);

        cachedSparqlService.executeQuery("t-evict", query);
        verify(engine, times(1)).executeQuery(anyString());

        cachedSparqlService.evictAll();

        cachedSparqlService.executeQuery("t-evict", query);
        verify(engine, times(2)).executeQuery(anyString());
    }

    @Test
    void evictForTenant() throws Exception {
        OntologyEngine engine = mock(OntologyEngine.class);
        SparqlQueryResult result = new SparqlQueryResult(List.of("s"), List.of(Map.of("s", "v1")), 1);
        String query = "SELECT ?s WHERE {?s ?p ?o} evictForTenant";

        given(engineRegistry.get("t-tenant")).willReturn(engine);
        given(engine.executeQuery(anyString())).willReturn(result);
        given(federatedQueryService.containsServiceClause(anyString())).willReturn(false);

        cachedSparqlService.executeQuery("t-tenant", query);
        verify(engine, times(1)).executeQuery(anyString());

        cachedSparqlService.evictForTenant("t-tenant");

        cachedSparqlService.executeQuery("t-tenant", query);
        verify(engine, times(2)).executeQuery(anyString());
    }

    @Test
    void concurrentAccess() throws Exception {
        OntologyEngine engine = mock(OntologyEngine.class);
        SparqlQueryResult result = new SparqlQueryResult(List.of("s"), List.of(Map.of("s", "v1")), 1);
        String query = "SELECT ?s WHERE {?s ?p ?o} concurrent";

        given(engineRegistry.get("t-concurrent")).willReturn(engine);
        given(engine.executeQuery(anyString())).willReturn(result);
        given(federatedQueryService.containsServiceClause(anyString())).willReturn(false);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    SparqlQueryResult r = cachedSparqlService.executeQuery("t-concurrent", query);
                    assertNotNull(r);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Concurrent execution timed out");
        assertEquals(0, errors.get());
        verify(engine, atMost(10)).executeQuery(anyString());
    }
}
