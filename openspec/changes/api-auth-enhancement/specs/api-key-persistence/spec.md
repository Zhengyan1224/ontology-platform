## ADDED Requirements

### Requirement: API key database table
The system SHALL store API keys in a database table `api_keys`.

- Table SHALL have columns: `id` (UUID PK), `key_hash` (SHA-256), `label`, `role`, `enabled`, `created_at`, `expires_at`
- The raw API key SHALL only be returned at creation time, never stored or logged afterwards

#### Scenario: API key created
- **WHEN** an admin creates a new API key via API
- **THEN** the response SHALL include the raw key value (once)
- **THEN** the key SHALL be stored as SHA-256 hash

### Requirement: API key CRUD API
The system SHALL provide admin endpoints for managing API keys.

- `GET /api/v1/admin/api-keys` — list all keys (without hash values)
- `POST /api/v1/admin/api-keys` — create a new key (returns raw key once)
- `DELETE /api/v1/admin/api-keys/{id}` — delete a key
- All endpoints SHALL require `ROLE_ADMIN`

#### Scenario: Admin creates API key
- **WHEN** an admin with `ROLE_ADMIN` sends POST to create a new key
- **THEN** a new key SHALL be generated and returned

### Requirement: ApiKeyFilter reads from database
The `ApiKeyFilter` SHALL validate `X-API-Key` against the database instead of the static list.

- Keys SHALL be cached in memory for performance (refresh interval configurable)
- Disabled or expired keys SHALL be rejected

#### Scenario: Valid DB-stored key passes
- **WHEN** a request includes `X-API-Key` with a key stored in the database
- **WHEN** the key is enabled and not expired
- **THEN** the request SHALL proceed

#### Scenario: Removed key rejected
- **WHEN** a key is deleted from the database
- **WHEN** a request uses that deleted key
- **THEN** the response SHALL have status 401

### Requirement: Startup seed from config
On first startup (empty `api_keys` table), the system SHALL seed API keys from `application.yml` `ontology.auth.api-keys`.

- Seeded keys SHALL be labeled with config key names and assigned `ROLE_ADMIN`
- Seed SHALL only run when the table is empty

#### Scenario: Seed on first startup
- **WHEN** the application starts with `api_keys` table empty
- **WHEN** `ontology.auth.api-keys` contains `admin-key-001`
- **THEN** the system SHALL insert `admin-key-001` into `api_keys` with `ROLE_ADMIN`
