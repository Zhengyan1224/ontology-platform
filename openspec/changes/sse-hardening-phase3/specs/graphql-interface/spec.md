## ADDED Requirements

### Requirement: GraphQL endpoint
The system SHALL provide a GraphQL API endpoint wrapping SPARQL query execution.

- Endpoint: `POST /api/v1/graphql`
- Content-Type: `application/json` (standard GraphQL over HTTP)
- The GraphQL schema SHALL expose one top-level query field per registered tenant
- Each tenant SHALL have the following query fields:
  - `sparql(query: String!, limit: Int, offset: Int): SparqlResult` — execute a raw SPARQL query
  - `nlq(question: String!, sessionId: String): NlqResult` — natural language query

#### Scenario: Raw SPARQL via GraphQL
- **WHEN** a GraphQL query `{ sample { sparql(query: "SELECT * WHERE { ?s ?p ?o } LIMIT 10") { variables rows } } }` is sent
- **THEN** the system SHALL execute the SPARQL against the "sample" tenant
- **THEN** the response SHALL contain variables and rows

#### Scenario: NLQ via GraphQL
- **WHEN** a GraphQL query `{ sample { nlq(question: "List all books") { sparql results } } }` is sent
- **THEN** the system SHALL process the NLQ and return the generated SPARQL and results

### Requirement: Schema-first GraphQL
The GraphQL schema SHALL be defined in a `.graphqls` schema file.

- Schema file location: `src/main/resources/graphql/schema.graphqls`
- Types SHALL be defined for `SparqlResult`, `NlqResult`, `QueryResult`, and related types
- The schema SHALL dynamically include tenant fields based on registered tenants
- Authentication SHALL use the same `X-API-Key` or `Authorization: Bearer` headers as the REST API

#### Scenario: GraphQL schema has tenant fields
- **WHEN** the GraphQL schema is introspected
- **THEN** each registered tenant SHALL appear as a field on the root `Query` type

### Requirement: Security integration
GraphQL endpoints SHALL reuse the existing Spring Security filter chain.

- `@PreAuthorize` SHALL apply to GraphQL operations
- Role requirements SHALL match the REST API equivalents:
  - `ROLE_READONLY`: can query SPARQL and NLQ
  - `ROLE_DEV`: same as readonly
  - `ROLE_ADMIN`: same as readonly

#### Scenario: Unauthenticated GraphQL returns 401
- **WHEN** a GraphQL request is sent without authentication
- **THEN** the response SHALL have status 401
