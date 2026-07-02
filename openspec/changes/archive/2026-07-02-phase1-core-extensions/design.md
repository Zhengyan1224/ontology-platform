## Context

The platform currently reads tenants from `application.yml` at startup and keeps them in memory via `TenantConfig`. The `OntologySchemaProvider` returns hardcoded strings for the two built-in tenants. Audit logs accumulate in `CopyOnWriteArrayList` and vanish on restart. None of these three concerns are testable at the integration level.

## Goals / Non-Goals

**Goals:**
- REST API for tenant CRUD (create, read, update, delete) at runtime
- Schema information parsed dynamically from OWL and OBDA files at runtime
- Audit logs persisted to an RDBMS table with filtering and retention
- All three features covered by integration tests

**Non-Goals:**
- Authentication/authorization for the new admin endpoints (separate change in Phase 2)
- Multi-region or distributed tenant configuration
- Audit log export, alerting, or visualization
- Dynamic schema discovery on the LLM path — the LLM prompt will use the same dynamic schema provider

## Decisions

### Decision 1: Tenant persistence — DB table over YAML file
- **Chosen**: Store runtime tenant configuration in a `tenants` database table alongside the audit logs. Boot-time tenants from `application.yml` are still loaded as defaults.
- **Alternatives considered**: YAML file rewriting — fragile, no transactional guarantees, concurrent modification risk. DB table gives us transactions, rollback, and easy querying.
- **Rationale**: The feature is "dynamic tenant management," and the only dynamic storage in the stack is the DB. Using the same H2 (or future PostgreSQL) instance keeps the architecture simple.

### Decision 2: Schema discovery — OWLAPI parsing vs. Ontop introspection
- **Chosen**: Parse OWL files with OWLAPI (already on the classpath via Ontop) to extract classes, class hierarchy, and properties. Parse OBDA files to extract mapping targets and source SQL tables.
- **Alternatives considered**: Querying Ontop's internal model after initialization — more tightly coupled and harder to test. Hardcoded strings (current) — doesn't scale.
- **Rationale**: OWLAPI is already a transitive dependency and gives us full access to the ontology model including annotations, restrictions, and axiom details. OBDA parsing can use simple line-based parsing for the `.obda` format or the Ontop `OBDAValidator` / `IOBDAFactory` if already available.

### Decision 3: Audit log storage — shared DB vs. separate schema
- **Chosen**: Shared application DB, new `audit_logs` table.
- **Rationale**: Simplest deployment. The application already has a DataSource and JdbcTemplate available via `spring-boot-starter-jdbc`. If performance becomes an issue, the table can be moved to a separate schema later without API changes.

### Decision 4: Audit log retention — application-level scheduled cleanup
- **Chosen**: A `@Scheduled` method in `AuditService` that deletes records older than a configurable threshold (`ontology.audit.retention-days`, default 90).
- **Alternatives considered**: Database TTL / event-driven — less portable across DB engines. Manual cleanup only — no guard against unbounded growth.
- **Rationale**: Simple, portable, and explicit in the codebase.

## Risks / Trade-offs

- **[Risk] Ontop's OWLAPI dependency version mismatch** → OWLAPI is pulled transitively by Ontop 5.5; using it directly means we're tied to Ontop's version. If Ontop upgrades to a newer OWLAPI, we may need to adapt. Mitigation: isolate OWL parsing behind a `SchemaParser` interface with Ontop's OWLAPI as one implementation.
- **[Risk] OBDA parsing fragile to format changes** → The `.obda` format is Ontop-specific and may change between versions. Mitigation: prefer Ontop's own parsing APIs over regex; wrap in an interface.
- **[Trade-off] DB-stored tenants are lost when using in-memory H2** → Mitigation: document that production deployments need a persistent DB. Boot tenants from `application.yml` survive restart as defaults.
- **[Trade-off] Integration tests will need real Ontop initialization** → Slower than pure mocks but necessary for confidence. Mitigation: use `@SpringBootTest` with a separate profile that limits tenant count.
