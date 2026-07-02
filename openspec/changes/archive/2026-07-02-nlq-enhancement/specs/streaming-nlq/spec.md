## ADDED Requirements

### Requirement: SSE streaming endpoint
The system SHALL provide a streaming NLQ endpoint using Server-Sent Events (SSE).

- Endpoint: `GET /api/v1/tenants/{tenantId}/nlq/stream?question={question}&sessionId={sessionId}`
- Response Content-Type SHALL be `text/event-stream`
- The endpoint SHALL use Spring's `SseEmitter` for implementation
- The emitter timeout SHALL be configurable via `ontology.nlq.stream.timeout` (default 60000ms)

#### Scenario: SSE stream emits events in order
- **WHEN** a valid NLQ request is sent to the SSE endpoint
- **THEN** the server SHALL emit a `status` event with `{"stage":"translating"}`
- **THEN** the server SHALL emit a `sparql` event with the generated SPARQL
- **THEN** the server SHALL emit a `result` event with query variables and rows
- **THEN** the server SHALL emit a `complete` event
- **THEN** the SSE connection SHALL close

### Requirement: Event types
The SSE endpoint SHALL support the following event types:

- `status`: Progress stage updates (`translating`, `executing`, `formatting`)
- `sparql`: The generated SPARQL query string
- `result`: Query results (variables + rows) and execution time
- `error`: Error message if the query fails
- `complete`: Signals the end of the stream

#### Scenario: Error during processing
- **WHEN** SPARQL generation fails
- **THEN** the server SHALL emit an `error` event with an error message
- **THEN** the server SHALL emit a `complete` event
- **THEN** the SSE connection SHALL close

### Requirement: Client disconnect handling
The system SHALL handle client disconnection gracefully.

#### Scenario: Client disconnects mid-query
- **WHEN** the SSE connection is closed by the client before the query completes
- **THEN** the system SHALL cancel any running LLM or database query
- **THEN** the system SHALL clean up session resources

### Requirement: Backward compatibility
The existing `POST /api/v1/tenants/{id}/nlq` endpoint SHALL continue to work unchanged.

#### Scenario: Blocking endpoint still functional
- **WHEN** a POST request is sent to the blocking NLQ endpoint
- **THEN** the response SHALL be identical to the pre-SSE implementation
