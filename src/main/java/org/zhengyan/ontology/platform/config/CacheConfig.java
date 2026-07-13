package org.zhengyan.ontology.platform.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${ontology.cache.sparql.max-size:500}")
    private int sparqlMaxSize;

    @Value("${ontology.cache.sparql.ttl-seconds:300}")
    private int sparqlTtlSeconds;

    @Value("${ontology.cache.query.max-size:100}")
    private int queryMaxSize;

    @Value("${ontology.cache.query.ttl-seconds:600}")
    private int queryTtlSeconds;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("sparqlResults", "queryResults");
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(sparqlMaxSize)
                .expireAfterWrite(sparqlTtlSeconds, TimeUnit.SECONDS)
                .recordStats());
        return manager;
    }
}
