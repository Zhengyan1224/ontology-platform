## ADDED Requirements

### Requirement: SPARQL query result size limit
The system SHALL enforce a configurable maximum number of result rows for SPARQL SELECT queries to protect against large result sets consuming excessive memory.

- The default max results value SHALL be 10000 rows
- The limit SHALL be configurable via `ontology.sparql.max-results` in `application.yml`
- Setting `ontology.sparql.max-results` to 0 SHALL disable the limit
- The limit SHALL be applied by injecting a `LIMIT` clause into the SPARQL query before execution when no `LIMIT` is already present
- The limit SHALL NOT modify an existing `LIMIT` clause if one is present
- The limit SHALL apply to query results only, not to intermediate computations

#### Scenario: Query without LIMIT gets default limit
- **WHEN** a SPARQL SELECT query without a `LIMIT` clause is executed
- **THEN** the system SHALL append `LIMIT 10000` to the query before execution

#### Scenario: Query with explicit LIMIT under limit is unchanged
- **WHEN** a SPARQL SELECT query with `LIMIT 50` is executed
- **THEN** the query SHALL NOT be modified

#### Scenario: max-results set to 0 disables limit
- **WHEN** `ontology.sparql.max-results` is set to 0
- **THEN** queries without a `LIMIT` clause SHALL NOT have a limit appended

#### Scenario: Non-SELECT queries are not affected
- **WHEN** a CONSTRUCT or DESCRIBE query is executed
- **THEN** the limit SHALL NOT be applied
