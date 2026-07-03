## 1. Auth Hardening

- [x] 1.1 Replace `ConcurrentHashMap` cache in `ApiKeyService` with Caffeine cache (configurable TTL + max size)
- [x] 1.2 Add Caffeine dependency to `pom.xml`
- [x] 1.3 Create `jwt_blacklist` DDL (`init-jwt-blacklist.sql`) and register in schema-locations
- [x] 1.4 Create `JwtBlacklistRepository` + scheduled cleanup task
- [x] 1.5 Update `JwtAuthFilter` to check blacklist on each request
- [x] 1.6 Create rate limiting filter (Bucket4j) protecting login/reinit/audit-clear endpoints
- [x] 1.7 Add Bucket4j dependency to `pom.xml`
- [x] 1.8 Wire rate limiting filter into `SecurityConfig` filter chain
- [x] 1.9 Add auth failure audit logging to `ApiKeyFilter`, `JwtAuthFilter`, and rate limit filter
- [x] 1.10 Update `application.yml` with `${ADMIN_PASSWORD}` and `${JWT_SECRET}` environment variable placeholders
- [x] 1.11 Add startup warning when default secrets detected
- [x] 1.12 Add `POST /api/v1/api-keys/{id}/revoke` and `POST /api/v1/auth/revoke-all` revocation endpoints

## 2. Query Caching

- [x] 2.1 Add `spring-boot-starter-cache` and Caffeine dependency to `pom.xml`
- [x] 2.2 Create `CacheConfig` — enable caching, configure Caffeine cache manager
- [x] 2.3 Add caching annotations to SPARQL execution path
- [x] 2.4 Implement tenant-aware cache eviction on reinit
- [x] 2.5 Add cache metrics (Micrometer `CacheMeterBinderProvider`)
- [x] 2.6 Add `POST /api/v1/admin/cache/clear` admin endpoint
- [x] 2.7 Add `ontology.cache.sparql.*` configuration properties

## 3. OWL Generation from SQL DDL

- [x] 3.1 Create `OwlGeneratorService` — reads `INFORMATION_SCHEMA` via JDBC, maps tables→classes, columns→properties, FKs→object properties
- [x] 3.2 Implement naming convention configuration (singularize, prefix, case)
- [x] 3.3 Add `POST /api/v1/tenants/{tenantId}/generate-owl` endpoint
- [x] 3.4 Add `ontology.owl-generation.*` configuration properties

## 4. Federated Query

- [x] 4.1 Create `FederatedQueryService` — parses SPARQL for `SERVICE <tenant:{id}>` patterns
- [x] 4.2 Integrate with `OntopEngine` to route SERVICE sub-queries to target tenant engines
- [x] 4.3 Add RBAC check for federated tenant access
- [x] 4.4 Add configurable timeout and concurrency limits

## 5. GraphQL Interface

- [x] 5.1 Add `spring-boot-starter-graphql` dependency to `pom.xml`
- [x] 5.2 Create `src/main/resources/graphql/schema.graphqls` with `SparqlResult` and `NlqResult` types
- [x] 5.3 Create `QueryDataFetcher` — wires SPARQL execution under tenant-specific fields
- [x] 5.4 Create `GraphQLController` or rely on auto-configured `/graphql` endpoint
- [x] 5.5 Ensure Spring Security filter chain applies to GraphQL endpoint

## 6. Ontology Visualization

- [x] 6.1 Create `OntologyGraphService` — converts OWL schema to `nodes`/`edges` JSON
- [x] 6.2 Create `GET /api/v1/tenants/{tenantId}/graph` endpoint
- [x] 6.3 Add per-tenant in-memory cache for graph data

## 7. Tests

- [x] 7.1 Write tests for auth hardening (rate limiting, JWT blacklist, cache eviction, audit logging)
- [x] 7.2 Write tests for query caching (hit/miss/eviction/clear)
- [x] 7.3 Write tests for OWL generation
- [x] 7.4 Write tests for federated query
- [x] 7.5 Write tests for GraphQL endpoint
- [x] 7.6 Write tests for ontology visualization
