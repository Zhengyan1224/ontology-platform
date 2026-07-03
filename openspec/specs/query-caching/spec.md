# Query Caching

## Purpose

Cache SPARQL query results in-memory to reduce repeated execution of identical queries, using Spring Cache + Caffeine.

## Requirements

### Requirement: SPARQL query result caching

The system SHALL cache SPARQL query results to reduce repeated execution of identical queries.

- Cache SHALL use Spring Cache abstraction with Caffeine as the backing store
- Cache key SHALL be derived from: `tenantId + queryText + offset + limit`
- Cache SHALL be configurable via `ontology.cache.sparql.*` properties:
  - `ttl`: Time-to-live in seconds (default 60)
  - `max-size`: Maximum number of cached queries (default 1000)
- Cache SHALL be disabled when `ontology.cache.sparql.enabled` is `false`

#### Scenario: Repeated query returns cached result
- **WHEN** the same SPARQL query is executed twice within the cache TTL
- **THEN** the second execution SHALL return the cached result

#### Scenario: Cache miss
- **WHEN** a SPARQL query is executed for the first time
- **THEN** the result SHALL be fetched from the Ontop engine and stored in the cache

#### Scenario: Cache TTL expired
- **WHEN** a cached query exceeds its TTL
- **THEN** the next execution SHALL re-fetch from the Ontop engine

### Requirement: Cache invalidation

The system SHALL support manual cache invalidation.

- `POST /api/v1/admin/cache/clear` SHALL clear all cached SPARQL results
- Cache SHALL be invalidated when a tenant is reinitialized or updated

#### Scenario: Admin clears cache
- **WHEN** an admin sends POST to `/api/v1/admin/cache/clear`
- **THEN** all cached SPARQL results SHALL be cleared

#### Scenario: Tenant reinit clears cache
- **WHEN** a tenant is reinitialized via `POST /tenants/{id}/reinit`
- **THEN** the cache entries for that tenant SHALL be cleared

### Requirement: Cache miss penalty transparency

The system SHALL expose cache statistics for monitoring.

- A metric SHALL track cache hit ratio
- The health endpoint SHALL include cache status

#### Scenario: Cache metrics available
- **WHEN** metrics are scraped from `/actuator/prometheus`
- **THEN** cache hit rate and size metrics SHALL be available
