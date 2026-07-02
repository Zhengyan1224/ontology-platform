## ADDED Requirements

### Requirement: Role hierarchy
The system SHALL support three roles with the following hierarchy:

- `ROLE_ADMIN` — full access to all endpoints
- `ROLE_DEV` — access to query endpoints (SPARQL, NLQ, schema, explain)
- `ROLE_READONLY` — read-only access (GET list endpoints, schema view)

`ROLE_ADMIN` SHALL inherit all permissions of `ROLE_DEV` and `ROLE_READONLY`.

#### Scenario: Admin can access all endpoints
- **WHEN** a request has `ROLE_ADMIN`
- **THEN** all API endpoints SHALL be accessible

#### Scenario: Dev cannot access admin endpoints
- **WHEN** a request has `ROLE_DEV`
- **WHEN** the endpoint is `/api/v1/tenants/{id}/reinit`
- **THEN** the response SHALL have status 403

### Requirement: Endpoint-role mapping
The system SHALL enforce the following role-per-endpoint mapping:

| Role | Allowed endpoints |
|------|------------------|
| Public | `/api/v1/health`, `/swagger-ui/**`, `/v3/api-docs/**`, `/h2-console/**`, `/api/v1/auth/login` |
| `ROLE_READONLY` | `GET /api/v1/tenants`, `GET /api/v1/tenants/{id}/schema`, `GET /api/v1/audit-log` |
| `ROLE_DEV` | All ROLE_READONLY + `POST /api/v1/tenants/{id}/sparql/**`, `POST /api/v1/tenants/{id}/nlq/**`, `GET /api/v1/tenants/{id}/nlq/stream`, `POST /api/v1/tenants/{id}/sparql/explain` |
| `ROLE_ADMIN` | All ROLE_DEV + `POST /api/v1/tenants` (create), `PUT /api/v1/tenants/{id}`, `DELETE /api/v1/tenants/{id}`, `POST /api/v1/tenants/{id}/reinit`, `POST /api/v1/audit-log/clear`, `GET /api/v1/admin/api-keys`, `POST /api/v1/admin/api-keys`, `DELETE /api/v1/admin/api-keys/{id}` |

#### Scenario: Readonly user can list tenants but not query
- **WHEN** a request has `ROLE_READONLY`
- **WHEN** sending GET to `/api/v1/tenants`
- **THEN** the request SHALL succeed
- **WHEN** sending POST to `/api/v1/tenants/sample/sparql`
- **THEN** the response SHALL have status 403

### Requirement: Role attached to API key
Each API key in the database SHALL have a `role` column determining its access level.

- JWT tokens SHALL embed the role from the authenticated API key
- `X-API-Key` authentication SHALL use the key's role from the database

#### Scenario: API key role enforced
- **WHEN** a `ROLE_READONLY` API key is used to access SPARQL endpoint
- **THEN** the response SHALL have status 403
