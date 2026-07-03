## Why

Federated cross-tenant SPARQL queries exist but lack per-tenant RBAC — the current `checkFederatedAccess()` only verifies global `ROLE_ADMIN`, violating the existing spec requirement that the caller must have access to both the source and each federated target tenant. Without this, a non-admin key with access to only one tenant could be used to probe other tenants via `SERVICE <tenant:...>`. Additionally, sub-queries share a single global timeout with no per-target-tenant granularity, and no metrics track federated query success/failure.

## What Changes

- Add `tenant_scopes` column to `api_keys` table so API keys can be scoped to specific tenants (`*` for all)
- Add `tenants` claim support to JWT tokens
- Create `TenantAccessEvaluator` utility to check principal access against a tenant ID
- Update `FederatedQueryService.checkFederatedAccess()` to verify access to both source and each federated target tenant
- Add per-sub-query timeout configuration (separate from the aggregate timeout)
- Add federated query metrics (counter + timer with federation tags)
- Enhance `AdminController` API key CRUD to accept/manage `tenantScopes`

## Capabilities

### New Capabilities
- `tenant-access-scope`: Tenant-scoped API keys and JWT tokens with `tenant_scopes` field
- `federated-query-metrics`: Micrometer metrics for federated query operations (counter + timer with federation tag)

### Modified Capabilities
- `federated-query`: Enhanced RBAC enforcement (per-tenant access check), per-sub-query timeout support

## Impact

- `db/init-api-keys.sql`: Add `tenant_scopes` column
- `ApiKeyEntity.java`: Add `tenantScopes` field
- `ApiKeyRepository.java`: Update RowMapper, save, and seed queries
- `ApiKeyService.java`: Update `generateKey()`, `seedKey()`, listing
- `ApiKeyFilter.java`: Pass tenant scopes to authentication token
- `JwtService.java`: Handle optional `tenants` claim
- `JwtAuthFilter.java`: Pass tenants to authentication token
- `FederatedQueryService.java`: Per-tenant RBAC check, per-sub-query timeout, metrics
- `FederatedQueryProperties.java`: Add `perSubqueryTimeoutMs` property
- `FederatedQueryServiceTest.java`: Expand with RBAC, timeout, and metrics tests
- `AdminController.java`: Accept `tenantScopes` in API key requests
