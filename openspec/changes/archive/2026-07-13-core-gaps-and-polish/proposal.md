## Why

The platform has several clear gaps in core SPARQL support (ASK queries not handled), missing validation for generated mappings, untracked SPARQL query history, and technical debt (generic `throws Exception`, monolithic `AdminController`). These issues reduce developer confidence and limit the platform's usability for real-world deployment.

## What Changes

- Add SPARQL ASK query support end-to-end: `OntopEngine`, `SparqlQueryResult`, `SparqlResultFormatter`, and frontend response display
- Replace `throws Exception` with typed `ObdaGenerationException` and structured error JSON responses in OBDA/OWL generation endpoints
- Add server-side mapping validation that parses generated OBDA and checks table/column/IRI correctness
- Split `AdminController` into focused controllers: `AdminController` (tenant+key management), `AuditController` (audit logs), `GenerationController` (OBDA/OWL generation and mapping download)
- Add per-API-key SPARQL query history: new `query_history` table, `QueryHistoryService`, controller endpoints, and a frontend history page

## Capabilities

### New Capabilities
- `sparql-ask-support`: SPARQL ASK (boolean) query execution, result serialization, and frontend response display
- `mapping-validation`: Server-side validation of generated OBDA mappings (syntax, table/column existence, IRI template correctness)
- `sparql-query-history`: Per-API-key SPARQL query history with pagination, persistence, and a dedicated frontend page

### Modified Capabilities
- *(No existing specs have requirement-level changes — error handling and controller split are implementation concerns)*

## Impact

- **New API endpoints**: `GET /api/v1/tenants/{id}/sparql/validate` (mapping validation), `GET /api/v1/tenants/{id}/query-history` (query history), `DELETE /api/v1/query-history/{id}` (delete a history entry)
- **New DB tables**: `query_history` (id, tenant_id, api_key_id, sparql, execution_time_ms, created_at)
- **Modified code**: `SparqlQueryResult` gains boolean query type; `AdminController` is split into 3 controllers; `ObdaGeneratorService` exceptions become typed
- **New frontend page**: `/query-history/` (static HTML)
