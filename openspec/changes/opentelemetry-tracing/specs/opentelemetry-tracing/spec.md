# OpenTelemetry Tracing

## ADDED Requirements

### Requirement: Auto-instrumentation via Java Agent

The system SHALL support OpenTelemetry Java Agent for automatic instrumentation of HTTP requests and JDBC queries.

- Agent SHALL be optional (not required for basic operation)
- Agent SHALL trace all HTTP requests to REST endpoints
- Agent SHALL trace all JDBC queries executed by Ontop engine
- Agent SHALL propagate trace context across thread pools

#### Scenario: HTTP request traced
- **WHEN** a client sends a request to any REST endpoint
- **THEN** the request SHALL create a trace with a span for the HTTP handler

### Requirement: Custom business spans via `@Observed`

The system SHALL add custom observation spans for key service methods.

- `SparqlController.executeQuery()` SHALL be annotated with `@Observed`
- `NaturalLanguageQueryService.answer()` SHALL be annotated with `@Observed`
- `FederatedQueryService.executeFederatedQuery()` SHALL be annotated with `@Observed`
- `CachedSparqlService.executeQuery()` SHALL be annotated with `@Observed`

#### Scenario: SPARQL query creates custom span
- **WHEN** a SPARQL query is executed
- **THEN** a custom span SHALL record the query execution time
- **THEN** the span SHALL have tag `query.type=sparql`

### Requirement: OTLP export

The system SHALL export traces via OTLP protocol.

- Traces SHALL be exported to a configurable OTLP endpoint
- Exporter SHALL be configurable via `application.yml`
- Sampling rate SHALL be configurable

#### Scenario: Traces exported to OTLP collector
- **WHEN** a request is processed and spans are created
- **THEN** traces SHALL be sent to the configured OTLP endpoint
