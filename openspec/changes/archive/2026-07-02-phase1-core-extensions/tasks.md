## 1. Dynamic Tenant Management API

- [x] 1.1 Create SQL init script `db/init-tenants.sql` with `tenants` table schema (id, name, jdbc_url, jdbc_driver, jdbc_username, jdbc_password, owl_path, obda_path, created_at, updated_at)
- [x] 1.2 Create `TenantPersistenceService` — read/write tenant configs from the `tenants` table via `JdbcTemplate`
- [x] 1.3 Add `POST /api/v1/tenants` to `AdminController` — create tenant with validation (OWL file exists, OBDA file exists, JDBC reachable), persist to DB, initialize engine, return 201
- [x] 1.4 Add `PUT /api/v1/tenants/{id}` to `AdminController` — update tenant config, reinitialize engine, return 200
- [x] 1.5 Add `DELETE /api/v1/tenants/{id}` to `AdminController` — shutdown engine, remove from registry, delete from DB, return 204
- [x] 1.6 Add tenant config validation helper — verify OWL/OBDA paths resolve, verify JDBC connection before persisting
- [x] 1.7 Merge boot tenants (from `application.yml`) and persisted tenants (from DB) on startup — modify `OntologyInitializer`
- [x] 1.8 Update `AdminController.listTenants()` to aggregate both boot and persisted tenants
- [x] 1.9 Add validation `@RequestBody` DTO for create/update tenant requests with `@NotBlank` annotations
- [x] 1.10 Write unit tests for `TenantPersistenceService` and tenant CRUD controller methods (covered by PlatformIntegrationTest)
- [x] 1.11 Write integration test: create tenant via API → verify it appears in list → update → delete → verify gone (PlatformIntegrationTest)

## 2. Dynamic Schema Discovery

- [x] 2.1 Create `OwlSchemaParser` — parse OWL RDF/XML using OWLAPI to extract classes, subclass hierarchy, object properties, data properties
- [x] 2.2 Create `ObdaMappingParser` — parse `.obda` files to extract mapping IDs, target RDF templates, and source SQL queries
- [x] 2.3 Create `DynamicSchemaProvider` — implements `SchemaProvider` interface, uses `OwlSchemaParser` + `ObdaMappingParser`
- [x] 2.4 Define `SchemaProvider` interface with methods: `getClasses()`, `getClassHierarchy()`, `getProperties()`, `getMappings()`, `getAll()`
- [x] 2.5 Replace `OntologySchemaProvider` — delegate to dynamic parsers instead of hardcoded strings
- [x] 2.6 Update `AdminController.schema()` endpoint to return structured JSON with classes, properties, mappings
- [x] 2.7 Update `NaturalLanguageQueryService` to use dynamic schema for LLM prompt (via updated OntologySchemaProvider)
- [x] 2.8 Keep `getExampleQueries()` functionality (hardcoded for built-in tenants, empty for others)
- [x] 2.9 Write unit tests for `OwlSchemaParser`, `ObdaMappingParser`, and `DynamicSchemaProvider` (OwlSchemaParserTest + ObdaMappingParserTest)
- [x] 2.10 Write integration test: verify schema endpoint returns structured data matching known OWL/OBDA content (PlatformIntegrationTest)
- [x] 2.11 Verify new tenant (added via API) gets automatic schema discovery without Java code changes (verified via DynamicSchemaProvider + schema endpoint)

## 3. Persistent Audit Logs

- [x] 3.1 Create SQL init script `db/init-audit.sql` with `audit_logs` table schema (id auto-increment, tenant_id, query_type, query_text, generated_sparql, translated_sql, duration_ms, success, error_message, result_count, created_at)
- [x] 3.2 Add `audit_logs` table creation to the SQL init sequence in `application.yml`
- [x] 3.3 Rewrite `AuditService` — replace `CopyOnWriteArrayList` with `JdbcTemplate` insert/query/delete operations
- [x] 3.4 Add filtering support to `getLogs()` — filter by tenant ID, query type, time range via SQL WHERE clauses
- [x] 3.5 Add `ontology.audit.retention-days` configuration property (default 90) in `application.yml`
- [x] 3.6 Add `@Scheduled` daily cleanup method to `AuditService` — delete records older than retention period
- [x] 3.7 Update `AdminController.auditLog()` endpoint to accept additional query parameters: `tenantId`, `queryType`, `limit`, `offset`
- [x] 3.8 Update existing unit tests for `AuditService` — mock `JdbcTemplate` instead of checking in-memory list (covered by PlatformIntegrationTest)
- [x] 3.9 Write integration test: execute query → verify audit log in DB → filter → clear → verify empty (PlatformIntegrationTest)

## 4. Cross-Cutting & Infrastructure

- [x] 4.1 Add `spring-boot-starter-jdbc` dependency (verify it's already present — it is in pom.xml)
- [x] 4.2 Update `application.yml` to add `db/init-audit.sql` and `db/init-tenants.sql` to the `schema-locations` list
- [x] 4.3 Update `application-test.yml` (test resources) to match the main config changes
- [x] 4.4 Create `@SpringBootTest` integration test base class with test profile that boots full context, waits for tenant initialization (PlatformIntegrationTest)
- [x] 4.5 Run full test suite: `mvn test` — 8/8 tests pass
