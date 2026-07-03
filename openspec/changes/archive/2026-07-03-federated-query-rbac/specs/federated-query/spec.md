## MODIFIED Requirements

### Requirement: SERVICE clause security

Federated SERVICE execution SHALL respect RBAC permissions with per-tenant granularity.

- The calling API key / JWT SHALL have access to BOTH the primary and each federated tenant
- Execution timeout SHALL be configurable via `ontology.federated-query.timeout` (default 30000ms)
- Per-sub-query timeout SHALL be configurable via `ontology.federated-query.per-subquery-timeout-ms` (default 0, meaning use aggregate timeout)
- Maximum number of concurrent federated sub-queries SHALL be configurable

#### Scenario: No access to federated tenant

- **WHEN** the caller has access to the primary tenant but not one or more federated tenants
- **THEN** the query SHALL be rejected with 403 Forbidden

#### Scenario: No access to primary tenant

- **WHEN** the caller does not have access to the primary tenant
- **THEN** the query SHALL be rejected with 403 Forbidden

#### Scenario: Federated query timeout

- **WHEN** an individual `SERVICE` sub-query exceeds the per-sub-query timeout
- **THEN** the sub-query SHALL be cancelled
- **THEN** the overall federated query SHALL return a timeout error

#### Scenario: Federated partial results timeout

- **WHEN** some sub-queries complete but others time out
- **THEN** the completed sub-queries SHALL NOT be used
- **THEN** the overall query SHALL return a timeout error
