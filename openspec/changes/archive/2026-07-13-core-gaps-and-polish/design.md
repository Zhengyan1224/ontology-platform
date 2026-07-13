## Context

The platform currently has 34 REST endpoints and 9 frontend pages. Based on the gap analysis:

- **SPARQL ASK**: `OntopEngine.executeQuery()` dispatches to `executeTupleQuery` or `executeGraphQuery` based on `trimmed.startsWith("CONSTRUCT") || trimmed.startsWith("DESCRIBE")`. ASK queries fall through to tuple execution, which fails at runtime because Ontop's `BooleanQuery` cannot be cast to `TupleQuery`.
- **Error handling**: `ObdaGeneratorService.generateObda()` and `OwlGeneratorService.generateOwl()` both declare `throws Exception`. `AdminController` handles them with generic catch blocks returning `INTERNAL_SERVER_ERROR`.
- **Mapping validation**: Generated OBDA is packed into ZIP without validation. Invalid mappings are only discovered when the engine fails to initialize.
- **Controller size**: `AdminController` has 18 public endpoints spanning tenant CRUD, generation, audit, cache management, and API keys — 496 lines with 12 injected dependencies and a `@SuppressWarnings("java:S107")` suppression.
- **Query history**: No tracking of executed SPARQL queries per user. Users cannot revisit or share their recent queries.

## Goals / Non-Goals

**Goals:**
- Add end-to-end SPARQL ASK support: engine → result model → formatter → controller → frontend
- Introduce typed `ObdaGenerationException` and `OwlGenerationException` extending `OntologyPlatformException`
- Create `ObdaMappingValidator` service that parses OBDA and validates table/column/IRI correctness
- Split `AdminController` into three focused controllers by responsibility domain
- Add persistent query history with per-API-key tracking, paginated API, and frontend page

**Non-Goals:**
- No changes to `OntologyEngine` interface (extend in backward-compatible way)
- No DB migration tool (still uses init-*.sql scripts)
- No changes to existing test structure (new features get new tests)
- No Docker, CI/CD, or PostgreSQL support (separate changes)

## Decisions

### SPARQL ASK Support
- **Result model**: Add `boolean booleanQueryResult` field to `SparqlQueryResult` + `BooleanQueryResult` enum value. New constructor `SparqlQueryResult(boolean askResult, long executionTimeMs)`.
- **Engine dispatch**: Change `isGraphQuery()` to a `getQueryType()` method returning enum `SELECT/CONSTRUCT/DESCRIBE/ASK`. Add `executeBooleanQuery()` branch calling `conn.prepareBooleanQuery().evaluate()`.
- **Controller**: When result is boolean:
  - `application/sparql-results+json` → standard SPARQL JSON boolean format `{"head":{},"boolean":true}`
  - `application/json` → `{"boolean": true, "executionTimeMs": ...}`
  - Other formats → 406 Not Acceptable
- **Frontend** (existing `/tenant/` page): Detect boolean response, display "true"/"false" badge with green/red styling.

### Error Handling规范化
- **New exception classes**: `ObdaGenerationException` (500, `OBDA_GENERATION_FAILED`), `OwlGenerationException` (500, `OWL_GENERATION_FAILED`). Both extend `OntologyPlatformException`.
- **Service changes**: `ObdaGeneratorService.generateObda()` and `OwlGeneratorService.generateOwl()` change signature to `throws ObdaGenerationException` / `throws OwlGenerationException`. Wrap `SQLException` and other checked exceptions inside.
- **Controller changes**: `AdminController`'s generation endpoints use the typed exceptions. `GlobalExceptionHandler` already handles `OntologyPlatformException` — no changes needed there.
- **Benefits**: Type-safe catches, structured error JSON (errorCode + message), no more `@SuppressWarnings` needed for the exception declaration.

### Controller 拆分
- Three controllers mapped to `/api/v1`:
  - `AdminController` → tenant CRUD (list, get, create, update, delete, reinit, graph, schema) + API key management. Keeps ~495 lines → ~250 lines.
  - `AuditController` → audit log endpoints (GET list, POST clear). ~50 lines.
  - `GenerationController` → OWL/OBDA generation + download + ZIP endpoints. All `@PreAuthorize("hasRole('ADMIN')")`. ~100 lines.
- All three share the same `@RequestMapping("/api/v1")` base path.
- No behavioral changes — only move methods and their injected dependencies to new classes.

### Mapping Validator
- **New service**: `ObdaMappingValidator` with one public method `validate(Tenant tenant, String obdaContent) → ValidationResult`.
- **Validation steps** (in order, stop on first failure):
  1. Parse OBDA with Ontop's `OBDAParser` (not directly available — need to verify API). Alternative: regex-based parsing of mapping IDs and source SQL.
  2. Extract table names from `source SELECT ... FROM <table>` clauses.
  3. Extract column names from `source` and `target` IRI template `{col}` references.
  4. Connect to tenant JDBC and verify all tables/columns exist.
  5. Verify each mapping has a primary key column in its target IRI template.
- **ValidationResult**: record with `valid boolean`, `errors List<String>`, `warnings List<String>`.
- **New endpoint**: `GET /api/v1/tenants/{id}/mapping/validate` (generates + validates on the fly). Returns ValidationResult as JSON.
- **Integration**: Can be called before `generate-mapping` ZIP endpoint returns, adding a `validation` field to the response.

### SPARQL Query History
- **New table**: `query_history` (id BIGINT AUTO_INCREMENT, tenant_id VARCHAR, api_key_id BIGINT, sparql CLOB, execution_time_ms BIGINT, created_at TIMESTAMP). Index on (api_key_id, created_at DESC).
- **New SQL init**: `init-query-history.sql`.
- **New model**: `QueryHistoryEntry` (id, tenantId, apiKeyId, sparql, executionTimeMs, createdAt).
- **New repository**: `QueryHistoryRepository` — JDBC-based, methods: `save(entry)`, `findByApiKey(apiKeyId, limit, offset)`, `findByTenant(tenantId, limit, offset)`, `deleteById(id)`.
- **New controller**: `QueryHistoryController` under `/api/v1`:
  - `GET /api/v1/tenants/{tenantId}/query-history` — paginated list for a tenant (all API keys), requires ADMIN.
  - `DELETE /api/v1/query-history/{id}` — delete entry, requires ADMIN.
- **Automatic recording**: Modify `SparqlController.doExecute()` and `doExecuteJson()` to record each successful query (after result is obtained) by resolving API key ID from security context.
- **Frontend page**: `/query-history/index.html` — shows table with sparql text, execution time, timestamp. Click to expand full SPARQL. Copy/execute buttons.
- **Retention**: Default 30-day retention, configurable via `ontology.query-history.retention-days`. A scheduled task (`@Scheduled`) purges old entries daily.

## Risks / Trade-offs

- **[Risk] Ontop OBDAParser API availability**: The Ontop API for parsing OBDA mappings may not be publicly accessible in Ontop 5.5. **Mitigation**: Fall back to regex-based parsing of the OBDA text format (structure is well-known: `[MappingDeclaration] @collection [[ ... mappingId ... target ... source ... ]]`).
- **[Risk] SPARQL ASK format negotiation**: The SPARQL protocol specifies `application/sparql-results+json` with `{"head":{},"boolean":true}`. Our existing JSON format uses `{"variables":[...],"results":[...]}`. **Mitigation**: Use a wrapper response that includes `queryType: "ASK"` alongside `boolean: true/false`.
- **[Trade-off] Query history storage**: Storing full SPARQL text in a CLOB column could grow large. **Mitigation**: Add a configurable retention period and daily purge. Also limit stored text to 10KB (truncate if longer).
- **[Trade-off] Controller split vs. PR size**: Splitting a 496-line controller into 3 files increases the total lines of code slightly (due to repeated constants like `KEY_ERROR`). This is acceptable for the maintainability gain.
