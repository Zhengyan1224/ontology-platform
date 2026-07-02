# Dynamic Tenant API

## Purpose

Enable runtime registration, update, and removal of tenants via REST API — tenants no longer require application.yml changes or restarts.

## Requirements

### Requirement: Create tenant via API
The system SHALL accept a POST request to `/api/v1/tenants` with a JSON body containing tenant configuration (id, name, jdbc-url, jdbc-driver, jdbc-username, jdbc-password, owl-path, obda-path). The system SHALL validate the configuration by attempting OWL file loading, OBDA file parsing, and JDBC connectivity before persisting. On success, the system SHALL initialize the Ontop engine for the new tenant and return the tenant info with HTTP 201.

#### Scenario: Successful tenant creation
- **WHEN** a POST request is sent to `/api/v1/tenants` with valid tenant JSON
- **THEN** the system returns HTTP 201 with the created tenant info, and the tenant appears in the GET /api/v1/tenants list

#### Scenario: Duplicate tenant ID
- **WHEN** a POST request is sent with an ID that already exists
- **THEN** the system returns HTTP 409 CONFLICT

#### Scenario: Invalid JDBC connection
- **WHEN** a POST request is sent with an unreachable JDBC URL
- **THEN** the system returns HTTP 422 with an error message describing the connection failure

#### Scenario: Missing OWL or OBDA file
- **WHEN** a POST request is sent with a non-existent OWL or OBDA path
- **THEN** the system returns HTTP 422 with an error message indicating the missing file

### Requirement: Update tenant via API
The system SHALL accept a PUT request to `/api/v1/tenants/{id}` with partial or full tenant configuration. The system SHALL validate the updated configuration, reinitialize the engine, and return the updated tenant info.

#### Scenario: Successful tenant update
- **WHEN** a PUT request is sent to `/api/v1/tenants/{id}` with updated JDBC credentials
- **THEN** the system re-initializes the engine with the new configuration and returns HTTP 200 with updated tenant info

#### Scenario: Update non-existent tenant
- **WHEN** a PUT request is sent for an unknown tenant ID
- **THEN** the system returns HTTP 404 NOT FOUND

### Requirement: Delete tenant via API
The system SHALL accept a DELETE request to `/api/v1/tenants/{id}`. The system SHALL shut down the engine, remove the tenant from the registry, and delete the persisted configuration.

#### Scenario: Successful tenant deletion
- **WHEN** a DELETE request is sent to `/api/v1/tenants/{id}` for an existing tenant
- **THEN** the system shuts down the engine, removes the tenant, and returns HTTP 204 NO CONTENT

#### Scenario: Delete non-existent tenant
- **WHEN** a DELETE request is sent for an unknown tenant ID
- **THEN** the system returns HTTP 404 NOT FOUND

### Requirement: Tenant configuration persistence
The system SHALL persist tenant configurations to the database so they survive application restarts. On startup, the system SHALL load both boot tenants (from `application.yml`) and persisted tenants (from the `tenants` table).

#### Scenario: Tenant survives restart
- **WHEN** a tenant is created via API, then the application is restarted
- **THEN** the tenant appears in the GET /api/v1/tenants list with a working engine

#### Scenario: Boot tenants and persisted tenants merge
- **WHEN** the application starts with both `application.yml` tenants and DB-persisted tenants
- **THEN** all tenants appear in the tenant list
