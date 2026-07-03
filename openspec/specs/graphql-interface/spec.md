# GraphQL Interface

## Purpose

Provide a GraphQL API endpoint wrapping SPARQL query execution, using `spring-boot-starter-graphql` schema-first approach.

## Requirements

### Requirement: GraphQL endpoint

The system SHALL provide a GraphQL API endpoint wrapping SPARQL query execution.

- Endpoint: `POST /api/v1/graphql`
- Content-Type: `application/json` (standard GraphQL over HTTP)
- The GraphQL schema SHALL expose query fields for SPARQL and NLQ execution
- Tenant selection SHALL be a parameter of each query field

#### Scenario: Raw SPARQL via GraphQL
- **WHEN** a GraphQL query `{ sparql(tenantId: "sample", query: "SELECT * WHERE { ?s ?p ?o } LIMIT 10") { variables rows } }` is sent
- **THEN** the system SHALL execute the SPARQL against the "sample" tenant
- **THEN** the response SHALL contain variables and rows

#### Scenario: NLQ via GraphQL
- **WHEN** a GraphQL query `{ nlq(tenantId: "sample", question: "List all books") { sparql results } }` is sent
- **THEN** the system SHALL process the NLQ and return the generated SPARQL and results

### Requirement: Schema-first GraphQL

The GraphQL schema SHALL be defined in a `.graphqls` schema file.

- Schema file location: `src/main/resources/graphql/schema.graphqls`
- Types SHALL be defined for `SparqlResult`, `NlqResult`, and related types
- Authentication SHALL use the same `X-API-Key` or `Authorization: Bearer` headers as the REST API

#### Scenario: GraphQL schema has query fields
- **WHEN** the GraphQL schema is introspected
- **THEN** the `Query` type SHALL have `sparql` and `nlq` fields

### Requirement: Security integration

GraphQL endpoints SHALL reuse the existing Spring Security filter chain.

- `@PreAuthorize` SHALL apply to GraphQL operations
- Role requirements SHALL match the REST API equivalents

#### Scenario: Unauthenticated GraphQL returns 401
- **WHEN** a GraphQL request is sent without authentication
- **THEN** the response SHALL have status 401
