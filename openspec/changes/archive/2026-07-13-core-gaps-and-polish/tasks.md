## 1. SPARQL ASK Support

- [x] 1.1 Add `QueryType` enum (SELECT, CONSTRUCT, DESCRIBE, BOOLEAN) and `booleanQueryResult` field to `SparqlQueryResult`
- [x] 1.2 Change `OntopEngine` to dispatch ASK queries via `prepareBooleanQuery().evaluate()` with `getQueryType()` detection
- [x] 1.3 Add ASK result serialization to `SparqlResultFormatter.writeBooleanResult()`
- [x] 1.4 Update `SparqlController` to handle boolean results (format negotiation, JSON wrapper, error for unsupported formats)
- [x] 1.5 Update frontend SPARQL page to detect and display boolean results as true/false badge
- [x] 1.6 Add integration test for ASK query in `PlatformIntegrationTest`

## 2. Error Handling规范化

- [x] 2.1 Create `ObdaGenerationException` and `OwlGenerationException` extending `OntologyPlatformException`
- [x] 2.2 Update `ObdaGeneratorService.generateObda()` signature to `throws ObdaGenerationException`, wrap `SQLException`
- [x] 2.3 Update `OwlGeneratorService.generateOwl()` signature to `throws OwlGenerationException`, wrap `SQLException`
- [x] 2.4 Remove `@SuppressWarnings("java:S107")` from `AdminController` constructor

## 3. Controller 拆分

- [x] 3.1 Create `AuditController` with `GET /audit-log` and `POST /audit-log/clear` (moved from `AdminController`)
- [x] 3.2 Create `GenerationController` with generation/download/ZIP endpoints (moved from `AdminController`), all `@PreAuthorize("hasRole('ADMIN')")`
- [x] 3.3 Refactor `AdminController` to retain only tenant CRUD, graph/schema, API key, cache/reinit endpoints
- [x] 3.4 Update `SecurityConfig` if any path-specific rules need adjustment (no changes needed)

## 4. Mapping Validator

- [x] 4.1 Create `ObdaMappingValidator` service with `validate(tenant, obdaContent)` returning `ValidationResult` record
- [x] 4.2 Implement OBDA parsing: extract mapping IDs, target IRIs, source SQL table/column references (regex-based fallback)
- [x] 4.3 Implement DB validation: connect to tenant JDBC and verify extracted tables/columns exist
- [x] 4.4 Add `GET /api/v1/tenants/{tenantId}/mapping/validate` endpoint returning `ValidationResult`
- [x] 4.5 Integrate validation into `POST /generate-mapping` ZIP response as additional field
- [x] 4.6 Add unit tests for `ObdaMappingValidator`

## 5. SPARQL Query History

- [x] 5.1 Create `init-query-history.sql` with `query_history` table DDL
- [x] 5.2 Create `QueryHistoryEntry` model class
- [x] 5.3 Create `QueryHistoryRepository` with JDBC save/findByApiKey/findByTenant/deleteById methods
- [x] 5.4 Add `application.yml` property `ontology.query-history.retention-days` (default 30)
- [x] 5.5 Create `QueryHistoryController` with `GET /api/v1/tenants/{tenantId}/query-history` and `DELETE /api/v1/query-history/{id}`
- [x] 5.6 Integrate recording into `SparqlController` (resolve API key from security context, save on success)
- [x] 5.7 Add `@Scheduled` daily purge of expired entries in `QueryHistoryRepository` or a new `QueryHistoryService`
- [x] 5.8 Create `/query-history/index.html` frontend page with tenant selector, paginated table, expand/copy/execute
- [x] 5.9 Update `SecurityConfig` to permit `/query-history/**` without auth
- [x] 5.10 Add integration test for query history endpoints

## 6. Test & Verify

- [x] 6.1 Run `mvn test` and confirm all tests pass (175 tests, 0 failures, 0 errors)
- [x] 6.2 Verify `mvn spring-boot:run` starts without errors
