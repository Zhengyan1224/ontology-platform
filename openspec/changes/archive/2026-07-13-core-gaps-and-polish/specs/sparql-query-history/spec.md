## ADDED Requirements

### Requirement: System SHALL automatically record SPARQL query history
The system SHALL record every successful SPARQL query execution with metadata.

#### Scenario: Record successful SELECT query
- **WHEN** a user executes a SPARQL SELECT query successfully
- **THEN** the system SHALL persist a `QueryHistoryEntry` with the SPARQL text, execution time, tenant ID, and API key ID

#### Scenario: Record CONSTRUCT/DESCRIBE query
- **WHEN** a user executes a CONSTRUCT or DESCRIBE query successfully
- **THEN** the system SHALL persist the entry with appropriate query metadata

#### Scenario: Do not record failed queries
- **WHEN** a SPARQL query throws an exception
- **THEN** the system SHALL NOT create a history entry

### Requirement: Admin SHALL list query history by tenant
The system SHALL provide a paginated API to retrieve query history.

#### Scenario: List recent queries for a tenant
- **WHEN** an admin sends GET `/api/v1/tenants/{tenantId}/query-history?limit=20&offset=0`
- **THEN** the system SHALL return the 20 most recent entries for that tenant, ordered by creation time descending

#### Scenario: Paginate through history
- **WHEN** an admin sends GET with offset=20
- **THEN** the system SHALL return the next page of results

#### Scenario: Empty history
- **WHEN** a tenant has no query history
- **THEN** the system SHALL return an empty array

### Requirement: Admin SHALL delete query history entries
The system SHALL allow admins to delete individual history entries.

#### Scenario: Delete a single entry
- **WHEN** an admin sends DELETE `/api/v1/query-history/{id}`
- **THEN** the system SHALL delete the entry and return 204 No Content

#### Scenario: Delete non-existent entry
- **WHEN** an admin sends DELETE with a non-existent id
- **THEN** the system SHALL return 404 Not Found

### Requirement: Query history SHALL auto-purge old entries
The system SHALL periodically purge entries older than a configurable retention period.

#### Scenario: Daily purge of expired entries
- **WHEN** the scheduled purge runs
- **THEN** the system SHALL delete all entries older than `ontology.query-history.retention-days` (default 30 days)

### Requirement: System SHALL provide a query history frontend page
The system SHALL have a static HTML page at `/query-history/` to browse and interact with history.

#### Scenario: History page loads
- **WHEN** a user navigates to `/query-history/`
- **THEN** the page SHALL load and display the query history table with tenant selector

#### Scenario: Expand SPARQL text
- **WHEN** a user clicks on a history row
- **THEN** the full SPARQL query text SHALL be displayed (expandable)

#### Scenario: Re-execute from history
- **WHEN** a user clicks "Execute" on a history entry
- **THEN** the page SHALL navigate to the tenant SPARQL page with the query pre-filled
