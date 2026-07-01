package org.zhengyan.ontology.platform.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Counter> queryCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> queryTimers = new ConcurrentHashMap<>();
    private final AtomicLong activeEngines = new AtomicLong(0);

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("ontology.engines.active", activeEngines);
    }

    public void recordQuery(String tenantId, long durationMs, boolean success) {
        String counterName = "ontology.queries." + (success ? "success" : "failure");
        Counter counter = queryCounters.computeIfAbsent(counterName + "." + tenantId,
                k -> Counter.builder(counterName)
                        .tag("tenant", tenantId)
                        .tag("status", success ? "success" : "failure")
                        .register(meterRegistry));
        counter.increment();

        Timer timer = queryTimers.computeIfAbsent("ontology.query.duration." + tenantId,
                k -> Timer.builder("ontology.query.duration")
                        .tag("tenant", tenantId)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry));
        timer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordNlq(String tenantId, long durationMs, boolean success, String mode) {
        Counter counter = Counter.builder("ontology.nlq.queries")
                .tag("tenant", tenantId)
                .tag("mode", mode)
                .tag("status", success ? "success" : "failure")
                .register(meterRegistry);
        counter.increment();

        Timer.builder("ontology.nlq.duration")
                .tag("tenant", tenantId)
                .tag("mode", mode)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordNlqQuery(String tenantId) {
        Counter.builder("ontology.nlq.queries")
                .tag("tenant", tenantId)
                .register(meterRegistry)
                .increment();
    }

    public void setActiveEngines(int count) {
        activeEngines.set(count);
    }
}
