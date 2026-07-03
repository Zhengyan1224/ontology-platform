## Context

Federated SPARQL queries use `SERVICE <tenant:{targetId}>` syntax. The current `FederatedQueryService` parses these clauses, executes sub-queries against target tenants concurrently, then substitutes results as `VALUES` before re-executing the modified query against the source tenant. Access control checks only global `ROLE_ADMIN`, ignoring the tenant-level scope implied by the existing spec.

API keys (`ApiKeyEntity`) have a `role` field but no `tenant_scopes` — they are effectively global. JWT tokens carry a `role` claim but have no `tenants` claim. There is no way to express "API key X can access only tenant `sample`".

## Goals / Non-Goals

**Goals:**
- Add `tenant_scopes` to API keys (comma-separated tenant IDs or `*` for all)
- Add optional `tenants` claim to JWT tokens
- Create `TenantAccessEvaluator` utility for centralized tenant access checks
- Enforce per-tenant access in `FederatedQueryService` — caller must have access to source AND each target tenant
- Add per-sub-query timeout configuration
- Add federated query Micrometer metrics (counter + timer with `federation=true` tag)

**Non-Goals:**
- Full RBAC overhaul on all endpoints (SparqlController, AdminController, etc.) — only federated query path
- DB-backed tenant-to-key join table — comma-separated `tenant_scopes` column is sufficient
- JWT token refresh or rotation

## Decisions

- **D1: Comma-separated `tenant_scopes` column over a join table** — Simpler schema, no migration complexity, sufficient for the platform's scale (dozens of tenants, not thousands). `*` wildcard means "all tenants".
- **D2: SemVer-style `perSubqueryTimeoutMs` in config** — Each sub-query gets its own timeout deadline via `CompletableFuture.orTimeout()`, independent of the aggregate timeout. If `0`, defaults to the aggregate timeout.
- **D3: Metric tags on existing counters/timers** — Reuse `MetricsService.recordQuery()` with an additional `federated` tag (`true`/`false`) rather than a separate metric namespace, keeping the existing query dashboard compatible.
- **D4: Authentication token carries `tenants` via details** — Use `UsernamePasswordAuthenticationToken.setDetails()` to pass the list of authorized tenant IDs, avoiding custom principal wrapper classes.

## Risks / Trade-offs

- [Risk] Wildcard `*` tenant scope may be too permissive for future multi-tenant isolation requirements → Mitigation: `*` is the default for backward compatibility; explicit scoping is opt-in.
- [Risk] Comma-separated `tenant_scopes` column has no referential integrity → Mitigation: tenant existence is validated at key creation time (not retroactively).
- [Risk] Adding `tenants` JWT claim changes token format — existing tokens will have no `tenants` claim, meaning they default to `*` (all tenants) → Mitigation: treat missing claim as `*` for backward compatibility.
