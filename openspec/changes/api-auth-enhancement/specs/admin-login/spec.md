## ADDED Requirements

### Requirement: Admin login endpoint
The system SHALL provide a `POST /api/v1/auth/login` endpoint for admin authentication.

- Accepts `{"username": "admin", "password": "..."}` in JSON body
- On success, returns `{"token": "<jwt>"}` with status 200
- On failure, returns 401 with error message
- Password SHALL be validated against a configurable admin password (`ontology.auth.admin-password`)

#### Scenario: Successful login
- **WHEN** a POST request is sent to `/api/v1/auth/login` with correct credentials
- **THEN** the response SHALL have status 200
- **THEN** the response SHALL contain a `token` field with a JWT string

#### Scenario: Failed login
- **WHEN** a POST request is sent with incorrect credentials
- **THEN** the response SHALL have status 401

### Requirement: JWT token validation
The system SHALL validate JWT tokens from the `Authorization: Bearer <token>` header.

- JWT SHALL contain `sub` (subject), `role`, `exp` (expiration) claims
- Expired tokens SHALL be rejected with 401
- Invalid/malformed tokens SHALL be rejected with 401
- JWT secret SHALL be configurable via `ontology.auth.jwt-secret`

#### Scenario: Valid JWT accesses protected endpoint
- **WHEN** a request includes `Authorization: Bearer <valid-jwt>` header
- **WHEN** the JWT has the required role for the endpoint
- **THEN** the request SHALL succeed

#### Scenario: Expired JWT rejected
- **WHEN** a request includes an expired JWT
- **THEN** the response SHALL have status 401
