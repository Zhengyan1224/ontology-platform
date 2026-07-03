## ADDED Requirements

### Requirement: API key tenant scoping

API keys SHALL support optional tenant-level scoping to restrict which tenants the key can access.

- The `api_keys` table SHALL have a `tenant_scopes` column (VARCHAR, default `*`)
- API keys with `tenant_scopes = *` SHALL have access to all tenants (default, backward compatible)
- API keys with a comma-separated list (e.g., `sample,university`) SHALL be restricted to those tenants only
- Tenant access SHALL be enforced on federated query paths
- The `POST /api-keys` endpoint SHALL accept an optional `tenantScopes` field

#### Scenario: API key created with specific tenant scopes

- **WHEN** an admin creates an API key with `tenantScopes: "sample,university"`
- **THEN** the key SHALL only be usable for operations targeting `sample` or `university`
- **THEN** attempts to access other tenants SHALL be rejected with 403

#### Scenario: API key created without tenant scopes defaults to all

- **WHEN** an admin creates an API key without specifying `tenantScopes`
- **THEN** the key SHALL have `tenant_scopes = *`
- **THEN** the key SHALL behave as before (access to all tenants)

### Requirement: JWT tenant claim

JWT tokens SHALL support an optional `tenants` claim for tenant-level scoping.

- The `JwtService.generateToken()` method SHALL accept an optional `tenants` parameter (comma-separated string or null)
- When present, the JWT SHALL include a `tenants` claim
- When absent, the JWT SHALL NOT include a `tenants` claim (treated as `*` all-tenants)
- The `JwtAuthFilter` SHALL extract the `tenants` claim and pass it through the authentication context

#### Scenario: JWT generated with tenants claim

- **WHEN** a JWT is generated with `tenants = "sample"`
- **THEN** the token SHALL contain a `tenants` claim with value `"sample"`
- **THEN** the authentication context SHALL carry the tenant scope

#### Scenario: JWT without tenants claim defaults to all

- **WHEN** a JWT is generated without a `tenants` parameter
- **THEN** the token SHALL NOT contain a `tenants` claim
- **THEN** the caller SHALL be treated as having access to all tenants

### Requirement: Tenant access evaluator

The system SHALL provide a centralized utility to evaluate whether a principal has access to a specific tenant.

- `TenantAccessEvaluator` SHALL accept a `Collection<? extends GrantedAuthority>` and a tenant ID
- `ROLE_ADMIN` SHALL have access to all tenants regardless of scope
- Non-admin principals SHALL be checked against their tenant scopes
- Missing tenant scopes SHALL be treated as access to all tenants

#### Scenario: Admin has access to any tenant

- **WHEN** the caller has `ROLE_ADMIN`
- **THEN** `hasAccess(auth, "anyTenant")` SHALL return `true`

#### Scenario: Non-admin with scoped key is denied access to other tenants

- **WHEN** the caller has tenant scopes `["sample"]` and tenant is `"university"`
- **THEN** `hasAccess(auth, "university")` SHALL return `false`
