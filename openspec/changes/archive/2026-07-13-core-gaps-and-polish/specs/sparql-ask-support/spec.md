## ADDED Requirements

### Requirement: OntopEngine SHALL support SPARQL ASK queries
The system SHALL execute SPARQL ASK (boolean) queries through the Ontop engine, returning true/false.

#### Scenario: Execute a valid ASK query
- **WHEN** a client sends an ASK SPARQL query via POST `/api/v1/tenants/{tenantId}/sparql`
- **THEN** the system SHALL evaluate the boolean query and return the result as a JSON object with a `boolean` field

#### Scenario: ASK query with Accept header
- **WHEN** a client sends an ASK query with `Accept: application/sparql-results+json`
- **THEN** the system SHALL return a standard SPARQL results JSON: `{"head":{},"boolean":true}`

#### Scenario: Non-boolean Accept for ASK
- **WHEN** a client sends an ASK query with `Accept: text/csv`
- **THEN** the system SHALL return HTTP 406 Not Acceptable

### Requirement: SparqlQueryResult SHALL support boolean results
The SparqlQueryResult model SHALL include a boolean result type marker and value.

#### Scenario: Boolean result model
- **WHEN** an ASK query is executed
- **THEN** the SparqlQueryResult SHALL have `queryType == QueryType.BOOLEAN` and `booleanQueryResult` set to true or false

#### Scenario: Default query type for SELECT
- **WHEN** a SELECT query is executed
- **THEN** the SparqlQueryResult SHALL have `queryType == QueryType.SELECT` (backward compatible)

### Requirement: Frontend SHALL display ASK results
The existing tenant SPARQL page SHALL display ASK query results as a clear true/false indicator.

#### Scenario: Display ASK result as badge
- **WHEN** a user executes an ASK query from the frontend
- **THEN** the page SHALL display a green "true" or red "false" badge in the results area
