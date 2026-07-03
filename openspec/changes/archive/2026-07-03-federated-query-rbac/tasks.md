## 1. Tenant Scope Data Model

- [ ] 1.1 Add `tenant_scopes VARCHAR(512) DEFAULT '*'` to `init-api-keys.sql`
- [ ] 1.2 Add `tenantScopes` field to `ApiKeyEntity` with getter/setter
- [ ] 1.3 Update `ApiKeyRowMapper.mapRow()` to read `tenant_scopes` column
- [ ] 1.4 Update `ApiKeyRepository.save()` INSERT to include `tenant_scopes`

## 2. Authentication — Tenant Scope Propagation

- [ ] 2.1 Update `JwtService.generateToken()` to accept optional `tenants` param and add `tenants` claim
- [ ] 2.2 Update `JwtService.extractAuthorities()` to return `tenants` through a custom wrapper (or use `Details` on the auth token)
- [ ] 2.3 Update `JwtAuthFilter` to extract `tenants` claim and set it as `authentication.details`
- [ ] 2.4 Update `ApiKeyService.seedKey()` / `generateKey()` to accept and persist `tenantScopes`
- [ ] 2.5 Update `ApiKeyFilter` to pass `tenantScopes` from `ApiKeyEntity` into `authentication.details`
- [ ] 2.6 Update `AdminController.ApiKeyRequest` and `POST /api-keys` to accept optional `tenantScopes`
- [ ] 2.7 Update `ApiKeySeeder` to seed API keys with tenant scopes from config

## 3. Tenant Access Evaluator

- [ ] 3.1 Create `TenantAccessEvaluator` utility class with `hasAccess(Authentication, String tenantId)` method
- [ ] 3.2 Support `ROLE_ADMIN` bypass (always has access)
- [ ] 3.3 Extract tenant scopes from `authentication.details` (comma-separated, `*` = all)
- [ ] 3.4 Write unit tests for `TenantAccessEvaluator` (admin bypass, scoped access, wildcard, missing details)

## 4. FederatedQueryService — RBAC + Timeout + Metrics

- [ ] 4.1 Inject `TenantAccessEvaluator` and `MetricsService` into `FederatedQueryService`
- [ ] 4.2 Update `checkFederatedAccess()` to verify access to source tenant AND all federated target tenants
- [ ] 4.3 Throw `403 Forbidden` with specific message indicating which tenant(s) are denied
- [ ] 4.4 Add `perSubqueryTimeoutMs` to `FederatedQueryProperties`
- [ ] 4.5 Apply `orTimeout(perSubqueryTimeoutMs)` to each individual sub-query future (not just aggregate)
- [ ] 4.6 Record federated query metrics via `MetricsService` with `federation=true` tag

## 5. Tests

- [ ] 5.1 Update `FederatedQueryServiceTest` with RBAC tests (admin access, scoped key denied, scoped key allowed, no auth, primary tenant denied)
- [ ] 5.2 Add per-sub-query timeout test
- [ ] 5.3 Add federated query metrics verification test
- [ ] 5.4 Run full test suite (mvn test) — 103+ tests passing
