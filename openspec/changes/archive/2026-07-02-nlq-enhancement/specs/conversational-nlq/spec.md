## ADDED Requirements

### Requirement: Session management
The system SHALL support NLQ sessions identified by a `sessionId` parameter.

- Sessions SHALL be managed by a dedicated `SessionManager` component
- Session data SHALL be stored in memory with TTL-based expiration
- Default TTL SHALL be 30 minutes since last activity
- Maximum session count SHALL be configurable (default 1000)
- When the limit is exceeded, the oldest session SHALL be evicted

#### Scenario: Session created on first request
- **WHEN** a new `sessionId` is provided in an NLQ request
- **THEN** the system SHALL create a new session
- **THEN** the session SHALL be stored with the current timestamp

#### Scenario: Session reused on subsequent request
- **WHEN** the same `sessionId` is provided in a subsequent NLQ request
- **THEN** the system SHALL retrieve the existing session context
- **THEN** the session TTL SHALL be refreshed

### Requirement: Conversation history
Each session SHALL maintain a history of the last N question-answer pairs (default N=5).

- Each history entry SHALL contain: `question` (user's NLQ), `sparql` (generated query), `summary` (optional human-readable interpretation)
- History SHALL be injected into the LLM prompt or template matching context

#### Scenario: History injected into LLM prompt
- **WHEN** a session has 3 prior Q/A pairs
- **WHEN** a new question is asked in the same session
- **THEN** the LLM prompt SHALL include the conversation history
- **THEN** the LLM SHALL be able to reference prior questions and results

### Requirement: Context-aware question resolution
The system SHALL resolve context-dependent questions (e.g., "they", "them", "those") using the conversation history.

- Resolve ambiguous references by examining the most recent SPARQL query variables and results
- If the new question contains "they/them/those", inject the previous query result values as context
- Log warning when context resolution is attempted but no history exists

#### Scenario: Follow-up question resolved via context
- **WHEN** session history contains: Q1="list all professors" with results
- **WHEN** user asks Q2="who works in CS department?"
- **THEN** the system SHALL use the full conversation history as prompt context
- **THEN** the SPARQL SHALL be generated independently (no result filtering applied from prior query)

### Requirement: Session cleanup
Expired sessions SHALL be periodically cleaned up.

#### Scenario: Expired session evicted
- **WHEN** a session has been inactive for longer than the TTL
- **THEN** the session SHALL be removed from memory on the next cleanup cycle
- **THEN** a new NLQ request with the expired sessionId SHALL create a fresh session
