## Why

After five phases of feature development (core → auth → NLQ → hardening → federated RBAC), the platform has 118 tests and the core APIs are stable. Two Phase 3 items remain incomplete (cache metrics granularity, OWL naming configuration), and several critical auth/caching modules lack direct unit tests — changes to these areas carry risk with no safety net.

## What Changes

- Add missing Caffeine cache metrics (hit count, miss count, eviction count, average load penalty)
- Fix `OwlGeneratorService.singularize()` bug (`statuses` → `statuse`, should be `status`)
- Add `outputDir` and `enabled` properties to `OwlGenerationProperties`
- Mark primary key columns as `owl:FunctionalProperty` in generated OWL
- Add `RateLimitFilterTest`, `JwtBlacklistRepositoryTest`, `ApiKeyServiceTest` (cache eviction), `JwtAuthFilterTest` (direct unit tests)
- Expand `GraphQlEndpointTest` to include auth integration
- Add missing test cases to `OwlGeneratorServiceTest`, `QueryCacheTest`

## Capabilities

### New Capabilities

(none — all changes are enhancements to existing capabilities)

### Modified Capabilities

- `query-caching`: Enhanced cache metrics requirement (hit count, miss count, eviction count, average load penalty)
- `sql-ddl-to-owl`: Enhanced OWL generation (fix singularize, add `owl:FunctionalProperty` for PKs, add `enabled`/`outputDir` config)

## Impact

- `CacheMetricsConfig.java`: Add more Caffeine stats gauges
- `OwlGeneratorService.java`: Fix `singularize()` method, add PK functional property marking
- `OwlGenerationProperties.java`: Add `outputDir`, `enabled` fields
- `RateLimitFilter.java`: No functional change (but need test access to verify)
- `JwtBlacklistRepository.java`: No functional change (test only)
- `ApiKeyService.java`: No functional change (test only)
- New test files: `RateLimitFilterTest.java`, `JwtBlacklistRepositoryTest.java`, `ApiKeyServiceTest.java`, `JwtAuthFilterTest.java`
