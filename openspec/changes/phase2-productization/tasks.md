## 1. API 认证与鉴权

- [ ] 1.1 Add `spring-boot-starter-security` to `pom.xml`
- [ ] 1.2 Add `ontology.auth.enabled` and `ontology.auth.api-keys` configuration to `application.yml` and test `application.yml`
- [ ] 1.3 Create `SecurityConfig` — `@EnableWebSecurity` + `SecurityFilterChain`, configure public paths (`/api/v1/health`, swagger, h2-console), conditionally disable auth via `ontology.auth.enabled`
- [ ] 1.4 Create `ApiKeyFilter` — `OncePerRequestFilter` that reads `X-API-Key` header, validates against configured keys, returns 401 on mismatch
- [ ] 1.5 Update `SparqlControllerTest`, `HealthControllerTest` to work with or without auth (test profile disables auth)
- [ ] 1.6 Write integration test: verify protected endpoint returns 401 without key, 200 with valid key

## 2. SPARQL 结果格式多样化

- [ ] 2.1 Create `SparqlResultFormat` enum with supported formats: JSON, SPARQL_JSON, SPARQL_XML, CSV, TSV, TURTLE, RDF_XML, JSON_LD
- [ ] 2.2 Create `SparqlResultFormatter` — strategy-based formatter that selects output format based on `Accept` header (with fallback to JSON)
- [ ] 2.3 Implement `TupleQueryResultFormatter` for SELECT queries: convert `TupleQueryResult` to SPARQL XML / CSV / TSV using RDF4J `SPARQLResultsXMLWriter`, `SPARQLResultsCSVWriter`, `SPARQLResultsTSVWriter`
- [ ] 2.4 Implement `GraphQueryResultFormatter` for CONSTRUCT queries: convert `GraphQueryResult` to Turtle / RDF/XML / JSON-LD using RDF4J Rio writers
- [ ] 2.5 Update `OntopEngine.executeQuery()` to detect query type (SELECT vs CONSTRUCT) and return unified result that preserves both tuple and graph data
- [ ] 2.6 Update `SparqlController` — parse `Accept` header, delegate to appropriate formatter, set `Content-Type` header on response, return 406 for unsupported formats
- [ ] 2.7 Write unit tests for `SparqlResultFormatter` — verify each format produces valid output
- [ ] 2.8 Write integration test: SPARQL SELECT with `Accept: text/csv` returns CSV content with correct Content-Type

## 3. NLQ 增强 — 数据驱动模板

- [ ] 3.1 Create YAML template file `src/main/resources/nlq-templates/sample.yml` — migrate 5 books regex templates from `SparqlTemplateGenerator`
- [ ] 3.2 Create YAML template file `src/main/resources/nlq-templates/university.yml` — migrate 7 university regex templates from `SparqlTemplateGenerator`
- [ ] 3.3 Create `NlqTemplateLoader` — loads YAML template files per tenant, validates format, caches in `ConcurrentHashMap`
- [ ] 3.4 Create `TemplateConfig` model class — `name`, `pattern`, `sparql`, `description` fields
- [ ] 3.5 Rewrite `SparqlTemplateGenerator` — delegate to `NlqTemplateLoader` instead of hardcoded regex list; extract tenant-specific pattern matching into config-driven loop
- [ ] 3.6 Add startup validation in `OntologyInitializer` — log warning if template YAML parsing fails but continue startup
- [ ] 3.7 Write unit test: `NlqTemplateLoader` loads sample.yml, matches a pattern, replaces parameters correctly

## 4. NLQ 增强 — LLM Prompt 优化

- [ ] 4.1 Update `NaturalLanguageQueryService` — enhance LLM prompt builder to include few-shot examples extracted from tenant's template config
- [ ] 4.2 Add system prompt section with clearer schema description (include property domains/ranges from `SchemaProvider`)
- [ ] 4.3 Add conversation history to LLM prompt when `X-Session-Id` is present (prepare for multi-turn in task 5)
- [ ] 4.4 Write unit test: verify LLM prompt contains expected few-shot examples

## 5. NLQ 增强 — 流式响应与多轮对话

- [ ] 5.1 Create `SessionManager` — `ConcurrentHashMap<String, SessionContext>` with 30-minute TTL, cleanup on `@Scheduled`, methods: `getOrCreateSession(sessionId)`, `addTurn(sessionId, question, sparql)`, `getHistory(sessionId)`
- [ ] 5.2 Create `SessionContext` model — `sessionId`, `createdAt`, `lastAccessedAt`, `List<ConversationTurn>` (max 5)
- [ ] 5.3 Update `NlqController` — add streaming endpoint or branch on `Accept: text/event-stream`; return `SseEmitter` that pushes `reasoning`, `generating`, `result` events
- [ ] 5.4 Update `NlqController` — read `X-Session-Id` header, pass to `NaturalLanguageQueryService`, return `X-Session-Id` in response for new sessions
- [ ] 5.5 Update `NaturalLanguageQueryService` — support `SseEmitter` callback for streaming LLM response; integrate `SessionManager` for multi-turn context
- [ ] 5.6 Write integration test: NLQ with SSE header returns `text/event-stream` with expected event types
- [ ] 5.7 Write integration test: NLQ follow-up with `X-Session-Id` uses previous conversation context

## 6. 交叉测试与验证

- [ ] 6.1 Run full test suite after each major section (auth, sparql, nlq), fix regressions
- [ ] 6.2 Verify `mvn test` passes: all existing 27 tests + all new tests
