## ADDED Requirements

### Requirement: Expose full Caffeine statistics

The cache metrics endpoint SHALL expose the following Caffeine statistics beyond hit rate and size:

- `ontology.cache.hit.count`: Total number of cache hits
- `ontology.cache.miss.count`: Total number of cache misses
- `ontology.cache.eviction.count`: Total number of cache evictions
- `ontology.cache.average.load.penalty`: Average time in milliseconds to load new values

#### Scenario: Full cache stats available in Prometheus

- **WHEN** metrics are scraped from `/actuator/prometheus`
- **THEN** all four additional metrics SHALL be available as Micrometer gauges
