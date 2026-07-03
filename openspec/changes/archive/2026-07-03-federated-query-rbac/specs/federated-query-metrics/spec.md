## ADDED Requirements

### Requirement: Federated query metrics

The system SHALL track federated query execution with Micrometer metrics.

- Federated query execution SHALL increment a counter with tag `federation=true`
- Federated query duration SHALL be recorded in a timer with tag `federation=true`
- Federated query failures SHALL increment a failure counter with tag `federation=true`

#### Scenario: Federated query success increments counter

- **WHEN** a federated SPARQL query completes successfully
- **THEN** the `ontology.queries.success` counter SHALL increment with tag `federation=true`

#### Scenario: Federated query failure records duration

- **WHEN** a federated SPARQL query fails (timeout or error)
- **THEN** the `ontology.query.duration` timer SHALL record the elapsed time with tag `federation=true`
- **THEN** the failure counter SHALL increment
