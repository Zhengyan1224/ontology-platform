## Context

The platform has 118 tests covering 20 test classes, but several critical security and caching modules lack direct unit tests. Caffeine cache metrics are partially exposed. OWL generation has a singularize bug and incomplete config wiring. These are independent, low-risk changes best batched into one sweep.

## Goals / Non-Goals

**Goals:**
- Add Caffeine `hitCount`, `missCount`, `evictionCount`, `averageLoadPenalty` to Micrometer
- Fix singularize bug (`statuses` → `Status` not `Statuse`)
- Add `outputDir`, `enabled` to `OwlGenerationProperties` (wire existing YAML values)
- Mark PK columns as `owl:FunctionalProperty` in generated OWL
- Add unit tests: `RateLimitFilterTest`, `JwtBlacklistRepositoryTest`, `ApiKeyServiceTest`, `JwtAuthFilterTest`
- Expand `GraphQlEndpointTest` with auth integration, `OwlGeneratorServiceTest` with naming modes, `QueryCacheTest` with concurrent access

**Non-Goals:**
- Test infrastructure changes (stay with JUnit 5 + Mockito)
- Library-based pluralizer (heuristic fixes only)
- Full end-to-end test for every endpoint

## Decisions

- **D1: Test auth filters directly with `MockHttpServletRequest`** — Use Spring's `MockHttpServletRequest`/`MockHttpServletResponse` instead of `WebApplicationContext` for faster unit tests on `OncePerRequestFilter` subclasses.
- **D2: OwlGenerationProperties wiring** — `outputDir` was already in YAML but not in the Properties class. Add the field with `@ConfigurationProperties` binding (Spring Boot auto-wires kebab-case to camelCase).
- **D3: Singularize fix** — Add explicit `sses` → `ss` rule before the general `ses` rule, and require minimum length checks to avoid over-trimming.
- **D4: PK detection** — Read `INFORMATION_SCHEMA.KEY_COLUMN_USAGE` / `TABLE_CONSTRAINTS` to identify PK columns, then emit `:prop a owl:FunctionalProperty` triples per PK column.

## Risks / Trade-offs

- [Risk] Adding `outputDir` changes `OwlGeneratorService` method signature if used → Mitigation: Store in `OwlGenerationProperties` and read inside the method; no signature change.
- [Risk] PK detection via INFORMATION_SCHEMA varies across DB dialects → Mitigation: Use the same `getSchema()` dialect detection already used by `readTables()`.
