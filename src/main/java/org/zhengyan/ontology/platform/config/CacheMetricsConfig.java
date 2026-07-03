package org.zhengyan.ontology.platform.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheMetricsConfig {

    @Bean
    public MeterBinder cacheMetrics(CacheManager cacheManager) {
        return (MeterRegistry registry) -> {
            for (String cacheName : cacheManager.getCacheNames()) {
                var cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    var nativeCache = cache.getNativeCache();
                    if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache) {
                        com.github.benmanes.caffeine.cache.Cache<?, ?> caffeineCache =
                                (com.github.benmanes.caffeine.cache.Cache<?, ?>) nativeCache;
                        var stats = caffeineCache.stats();
                        io.micrometer.core.instrument.Gauge.builder(
                                "ontology.cache." + cacheName + ".hit.rate",
                                stats, s -> s.hitRate()
                        ).description("Hit rate for cache " + cacheName).register(registry);
                        io.micrometer.core.instrument.Gauge.builder(
                                "ontology.cache." + cacheName + ".size",
                                caffeineCache, c -> c.estimatedSize()
                        ).description("Current size of cache " + cacheName).register(registry);
                        io.micrometer.core.instrument.Gauge.builder(
                                "ontology.cache." + cacheName + ".hit.count",
                                stats, s -> s.hitCount()
                        ).description("Total hit count for cache " + cacheName).register(registry);
                        io.micrometer.core.instrument.Gauge.builder(
                                "ontology.cache." + cacheName + ".miss.count",
                                stats, s -> s.missCount()
                        ).description("Total miss count for cache " + cacheName).register(registry);
                        io.micrometer.core.instrument.Gauge.builder(
                                "ontology.cache." + cacheName + ".eviction.count",
                                stats, s -> s.evictionCount()
                        ).description("Total eviction count for cache " + cacheName).register(registry);
                        io.micrometer.core.instrument.Gauge.builder(
                                "ontology.cache." + cacheName + ".average.load.penalty",
                                stats, s -> s.averageLoadPenalty()
                        ).description("Average load penalty (ns) for cache " + cacheName).register(registry);
                    }
                }
            }
        };
    }
}
