## 1. API 认证与鉴权

- [x] 1.1 Add `spring-boot-starter-security` to `pom.xml`
- [x] 1.2 Add `ontology.auth.enabled` and `ontology.auth.api-keys` configuration to `application.yml` and test `application.yml`
- [x] 1.3 Create `SecurityConfig` — `@EnableWebSecurity` + `SecurityFilterChain`, configure public paths (`/api/v1/health`, swagger, h2-console), conditionally disable auth via `ontology.auth.enabled`
- [x] 1.4 Create `ApiKeyFilter` — `OncePerRequestFilter` that reads `X-API-Key` header, validates against configured keys, returns 401 on mismatch
- [x] 1.5 Update `SparqlControllerTest`, `HealthControllerTest` to work with or without auth (test profile disables auth)
- [x] 1.6 Write integration test: verify protected endpoint returns 401 without key, 200 with valid key

## 2. SPARQL 结果格式多样化

- [x] 2.1 Create `SparqlResultFormat` enum with supported formats: JSON, SPARQL_JSON, SPARQL_XML, CSV, TSV, TURTLE, RDF_XML, JSON_LD
- [x] 2.2 Create `SparqlResultFormatter` — strategy-based formatter that selects output format based on `Accept` header (with fallback to JSON)
- [x] 2.3 Implement `TupleQueryResultFormatter` for SELECT queries: convert `TupleQueryResult` to SPARQL XML / CSV / TSV using RDF4J `SPARQLResultsXMLWriter`, `SPARQLResultsCSVWriter`, `SPARQLResultsTSVWriter`
- [x] 2.4 Implement `GraphQueryResultFormatter` for CONSTRUCT queries: convert `GraphQueryResult` to Turtle / RDF/XML / JSON-LD using RDF4J Rio writers
- [x] 2.5 Update `OntopEngine.executeQuery()` to detect query type (SELECT vs CONSTRUCT) and return unified result that preserves both tuple and graph data
- [x] 2.6 Update `SparqlController` — parse `Accept` header, delegate to appropriate formatter, set `Content-Type` header on response, return 406 for unsupported formats
- [x] 2.7 Write unit tests for `SparqlResultFormatter` — verify each format produces valid output
- [x] 2.8 Write integration test: SPARQL SELECT with `Accept: text/csv` returns CSV content with correct Content-Type

## 3. NLQ 增强 — 数据驱动模板

- [x] 3.1 Create YAML template file `src/main/resources/nlq-templates/sample.yml` — migrate 5 books regex templates from `SparqlTemplateGenerator`
- [x] 3.2 Create YAML template file `src/main/resources/nlq-templates/university.yml` — migrate 7 university regex templates from `SparqlTemplateGenerator`
- [x] 3.3 Create `NlqTemplateLoader` — loads YAML template files per tenant, validates format, caches in `ConcurrentHashMap`
- [x] 3.4 Create `TemplateConfig` model class — `name`, `pattern`, `sparql`, `description` fields
- [x] 3.5 Rewrite `SparqlTemplateGenerator` — delegate to `NlqTemplateLoader` instead of hardcoded regex list; extract tenant-specific pattern matching into config-driven loop
- [x] 3.6 Add startup validation in `OntologyInitializer` — log warning if template YAML parsing fails but continue startup
- [x] 3.7 Write unit test: `NlqTemplateLoader` loads sample.yml, matches a pattern, replaces parameters correctly

## 4. NLQ 增强 — LLM Prompt 优化

- [x] 4.1 Update `NaturalLanguageQueryService` — enhance LLM prompt builder to include few-shot examples extracted from tenant's template config
- [x] 4.2 Add system prompt section with clearer schema description (include property domains/ranges from `SchemaProvider`)
- [x] 4.3 Add conversation history to LLM prompt when `X-Session-Id` is present (prepare for multi-turn in task 5)
- [x] 4.4 Write unit test: verify LLM prompt contains expected few-shot examples

## 5. NLQ 增强 — 流式响应与多轮对话

- [x] 5.1 Create `SessionManager` — `ConcurrentHashMap<String, SessionContext>` with 30-minute TTL, cleanup on `@Scheduled`, methods: `getOrCreateSession(sessionId)`, `addTurn(sessionId, question, sparql)`, `getHistory(sessionId)`
- [x] 5.2 Create `SessionContext` model — `sessionId`, `createdAt`, `lastAccessedAt`, `List<ConversationTurn>` (max 5)
- [x] 5.3 Update `NlqController` — add streaming endpoint or branch on `Accept: text/event-stream`; return `SseEmitter` that pushes `reasoning`, `generating`, `result` events
- [x] 5.4 Update `NlqController` — read `X-Session-Id` header, pass to `NaturalLanguageQueryService`, return `X-Session-Id` in response for new sessions
- [x] 5.5 Update `NaturalLanguageQueryService` — support `SseEmitter` callback for streaming LLM response; integrate `SessionManager` for multi-turn context
- [x] 5.6 Write integration test: NLQ with SSE header returns `text/event-stream` with expected event types
- [x] 5.7 Write integration test: NLQ follow-up with `X-Session-Id` uses previous conversation context

## 6. 交叉测试与验证

- [x] 6.1 Run full test suite after each major section (auth, sparql, nlq), fix regressions
- [x] 6.2 Verify `mvn test` passes: all existing 27 tests + all new tests

## 7. API Key 数据库持久化

- [x] 7.1 Create `init-api-keys.sql` DDL (`api_keys` table with `id`, `key_hash`, `name`, `role`, `enabled`, `created_at`, `updated_at`)
- [x] 7.2 Create `ApiKeyEntity` and `ApiKeyRepository` (Spring Data JPA)
- [x] 7.3 Create `ApiKeyAdminService` — CRUD + SHA-256 hashing on save; integrate with existing `ApiKeyService` cache
- [x] 7.4 Implement startup seed: import static keys from `application.yml` on first boot (when DB empty)
- [x] 7.5 Refactor `ApiKeyFilter` / `ApiKeyService` to validate against DB instead of config
- [x] 7.6 Add `POST /api/v1/admin/api-keys`, `GET /api/v1/admin/api-keys`, `DELETE /api/v1/admin/api-keys/{id}` endpoints
- [x] 7.7 Write integration tests for API key CRUD + DB-backed validation
