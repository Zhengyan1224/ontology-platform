## 1. Cache Metrics Enhancement

- [x] 1.1 Add `hitCount`, `missCount`, `evictionCount`, `averageLoadPenalty` gauges to `CacheMetricsConfig`
- [x] 1.2 Verify metrics appear in `/actuator/prometheus` with correct tags

## 2. OWL Generation Fixes

- [x] 2.1 Fix `singularize()` to handle `sses` → `ss` before `ses` rule (fix `statuses` → `Status`)
- [x] 2.2 Add `outputDir`, `enabled` fields to `OwlGenerationProperties`
- [x] 2.3 Read `DatabaseMetaData.getPrimaryKeys()` to detect PK columns and emit `owl:FunctionalProperty` triples
- [x] 2.4 Update `OwlGeneratorServiceTest` with: camelCase mode, custom prefixes, PK functional property, empty DB, singularize edge cases

## 3. Missing Unit Tests

- [x] 3.1 `RateLimitFilterTest`: mock request/response, test `tryConsume` true/false paths, test `isRateLimited()` path matching
- [x] 3.2 `JwtBlacklistRepositoryTest`: test save/exists/purgeExpired/isSubjectRevoked
- [x] 3.3 `ApiKeyServiceTest`: test Caffeine cache eviction on toggle/delete, expiry behavior
- [x] 3.4 `JwtAuthFilterTest`: test blacklist check, expired token, malformed token, valid token paths
- [x] 3.5 Expand `GraphQlEndpointTest`: enable auth filter, test valid/invalid API key, test RBAC
- [x] 3.6 Expand `QueryCacheTest`: add concurrent access scenario

---

**Result:** All 12 tasks completed. 149 tests (up from 115), 0 failures.
