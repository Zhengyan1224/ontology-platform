# Auth Hardening

## Purpose

Hardening the authentication system with API key caching, JWT revocation, rate limiting, auth audit logging, and environment-variable-based secret injection.

## Requirements

### Requirement: ApiKeyCache eviction

The `ApiKeyService` in-memory cache SHALL have configurable eviction policies.

- Cache SHALL use Caffeine with configurable maximum size (default 1000 entries)
- Cache entries SHALL expire after a configurable TTL (default 300 seconds)
- Cache SHALL be invalidated when an API key is toggled, deleted, or updated via admin endpoints
- The cache SHALL evict least-recently-used entries when max size is reached

#### Scenario: Cache evicts old entries
- **WHEN** the API key cache exceeds maximum size
- **THEN** the least-recently-used entry SHALL be evicted

#### Scenario: Expired cache entry re-fetched from DB
- **WHEN** a cached API key entry exceeds TTL
- **THEN** the next validation SHALL re-fetch from the database

#### Scenario: Admin toggles key and cache invalidates
- **WHEN** an admin toggles an API key's enabled status
- **THEN** the cache SHALL be invalidated for that key

### Requirement: JWT blacklist

The system SHALL support JWT token revocation before expiration.

- A database table `jwt_blacklist` SHALL store revoked token IDs (`jti`)
- The `JwtAuthFilter` SHALL check the blacklist on every request
- Expired blacklist entries SHALL be cleaned up by a scheduled task
- When the admin password is changed, all existing JWT tokens SHALL be invalidated

#### Scenario: JWT revoked
- **WHEN** an admin requests revocation of a JWT token
- **THEN** the token's `jti` SHALL be added to `jwt_blacklist`
- **THEN** subsequent requests with that token SHALL return 401

#### Scenario: Expired blacklist entry cleaned
- **WHEN** a JWT token in the blacklist has passed its expiration time
- **THEN** the scheduled cleanup SHALL remove it from the blacklist

#### Scenario: Password change invalidates all tokens
- **WHEN** the admin password is changed
- **THEN** all existing JWT tokens SHALL be invalidated

### Requirement: Rate limiting for sensitive endpoints

The system SHALL rate-limit sensitive admin endpoints to prevent abuse.

- Rate limiting SHALL use token-bucket algorithm (Bucket4j)
- Rate limit SHALL apply to: `POST /api/v1/auth/login`, `POST /api/v1/tenants/{id}/reinit`, `POST /api/v1/audit-log/clear`
- Rate limit SHALL be configurable via `ontology.ratelimit.*` properties
- Default SHALL be 5 requests per minute per client IP
- Rate-limited requests SHALL return 429 Too Many Requests with `Retry-After` header
- Non-admin endpoints SHALL NOT be rate-limited

#### Scenario: Rate limit exceeded
- **WHEN** more than 5 login requests arrive from the same IP within a minute
- **THEN** the 6th request SHALL return status 429 with `Retry-After` header

#### Scenario: Rate limit not exceeded
- **WHEN** 3 login requests arrive from the same IP within a minute
- **THEN** all requests SHALL succeed normally

### Requirement: Auth failure audit logging

The system SHALL log authentication failures to the `audit_logs` table.

- Every authentication failure SHALL be recorded with: timestamp, client IP, `X-API-Key` prefix (if provided), failure reason
- API key lookup failures, JWT validation failures, and rate-limit blocks SHALL all be logged
- Successful authentications SHALL NOT be logged (to avoid log noise)
- Auth audit entries SHALL have `query_type = 'AUTH'`

#### Scenario: Invalid API key logged
- **WHEN** a request with an invalid `X-API-Key` is received
- **THEN** an audit log entry SHALL be created with `query_type = 'AUTH'` and the failure reason

#### Scenario: Rate limit block logged
- **WHEN** a request is blocked by rate limiting
- **THEN** an audit log entry SHALL be created with the rate-limit reason

### Requirement: Environment variable secrets

The system SHALL support injecting `ontology.auth.admin-password` and `ontology.auth.jwt.secret` via environment variables.

- `application.yml` SHALL use `${ADMIN_PASSWORD}` and `${JWT_SECRET}` placeholders
- When the environment variables are not set, the default values from `application.yml` SHALL be used
- A startup warning SHALL be logged if default secrets are in use

#### Scenario: Environment variable overrides config
- **WHEN** the `ADMIN_PASSWORD` environment variable is set
- **THEN** the system SHALL use the environment variable value instead of the `application.yml` default

#### Scenario: Default secret warning
- **WHEN** the system starts with default secrets in `application.yml`
- **THEN** a warning SHALL be logged indicating that default secrets are in use

### Requirement: Admin API key revocation endpoint

The system SHALL provide an admin endpoint to revoke all sessions for a given API key or JWT.

- `POST /api/v1/api-keys/{id}/revoke` SHALL disable the key and add all active JWTs to blacklist
- `POST /api/v1/auth/revoke-all` SHALL invalidate all JWT tokens (password change workflow)

#### Scenario: Revoke API key
- **WHEN** an admin sends POST to `/api/v1/api-keys/{id}/revoke`
- **THEN** the API key SHALL be disabled
- **THEN** any active JWTs for that key SHALL be invalidated
