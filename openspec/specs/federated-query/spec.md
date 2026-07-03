# Federated Query

## Purpose

Support cross-tenant federated SPARQL queries using a custom `SERVICE <tenant:xxx>` URI scheme, with security and timeout controls.

## Requirements

### Requirement: Cross-tenant federated SPARQL queries

The system SHALL support federated SPARQL queries across tenants using a custom `SERVICE` URI scheme.

- SPARQL queries SHALL support `SERVICE <tenant:{tenantId}>` syntax in WHERE clauses
- The Ontop engine layer SHALL intercept `SERVICE` clauses with the `tenant:` URI scheme
- Each `SERVICE` call SHALL create a temporary query execution against the target tenant's engine
- Results from all `SERVICE` calls SHALL be joined with the main query results
- The tenant referenced in `SERVICE` MUST be registered in the system

#### Scenario: Cross-tenant query returns joined results
- **WHEN** a SPARQL query contains `SERVICE <tenant:university>` in the WHERE clause
- **THEN** the system SHALL execute the SERVICE subquery against the "university" tenant
- **THEN** the results SHALL be joined with the main query

#### Scenario: Unknown tenant in SERVICE returns error
- **WHEN** a SPARQL query contains `SERVICE <tenant:nonexistent>`
- **THEN** the system SHALL return an error indicating the tenant does not exist

### Requirement: SERVICE clause security

Federated SERVICE execution SHALL respect RBAC permissions.

- The calling API key / JWT SHALL have access to BOTH the primary and the federated tenant
- Execution timeout SHALL be configurable via `ontology.federated-query.timeout` (default 30000ms)
- Maximum number of concurrent federated sub-queries SHALL be configurable

#### Scenario: No access to federated tenant
- **WHEN** the caller has access to the primary tenant but not the federated tenant
- **THEN** the query SHALL be rejected with 403 Forbidden

#### Scenario: Federated query timeout
- **WHEN** a `SERVICE` sub-query exceeds the configured timeout
- **THEN** the sub-query SHALL be cancelled
- **THEN** an error SHALL be returned for the federated portion
