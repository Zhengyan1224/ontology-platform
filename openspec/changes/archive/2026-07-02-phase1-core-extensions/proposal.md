## Why

The current platform is a functional prototype but has three critical gaps that prevent production use and multi-tenant self-service: tenants are hardcoded in YAML (cannot be added/dynamified at runtime), schema information is hardcoded in Java (requires code changes for new tenants), and audit logs are stored in memory only (lost on restart). Closing these gaps makes the platform truly multi-tenant and production-deployable.

## What Changes

- **Dynamic Multi-Tenant Management API** — Add REST endpoints to create, update, and delete tenants at runtime without server restart. Persist tenant configuration to a database table or YAML file. Validate tenant connectivity (OWL, OBDA, JDBC) on creation.
- **Dynamic Schema Discovery** — Replace the hardcoded `OntologySchemaProvider` with runtime parsing of OWL and OBDA files. Extract classes, properties, class hierarchies, and mappings dynamically. The `/schema` endpoint and LLM prompts will use these discovered schemas automatically.
- **Persistent Audit Logs** — Migrate audit logs from in-memory `CopyOnWriteArrayList` to database storage. Create an `audit_logs` table, support filtering by tenant/type/time range, and add configurable retention policy for automatic cleanup.

## Capabilities

### New Capabilities
- `dynamic-tenant-api`: RESTful tenant CRUD at runtime, with persistence and validation
- `dynamic-schema-discovery`: Runtime OWL/OBDA parsing, replacing hardcoded schema provider
- `persistent-audit-logs`: Database-backed audit logging with filtering and retention

### Modified Capabilities
*(None — no existing specs are being modified)*

## Impact

- **Controllers**: New `TenantAdminController` or extended `AdminController`
- **Services**: New `TenantPersistenceService`, overhaul of `OntologySchemaProvider` and `AuditService`
- **Model**: New `AuditLogEntry` (or extend existing `QueryAuditLog`)
- **Database**: New `audit_logs` table; optional `tenants` table
- **Configuration**: `application.yml` remains for bootstrapping default tenants; runtime tenants stored separately
- **Build**: No new dependencies anticipated
