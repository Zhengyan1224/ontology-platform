## ADDED Requirements

### Requirement: Audit logs stored in database
The system SHALL store audit log records in an `audit_logs` database table instead of in-memory. Each record SHALL contain: id, tenant_id, query_type (SPARQL/NLQ), query_text, generated_sparql, translated_sql, duration_ms, success, error_message, result_count, and created_at.

#### Scenario: SPARQL query is logged to DB
- **WHEN** a SPARQL query is executed
- **THEN** an audit_logs record is created in the database with the query details

#### Scenario: NLQ query is logged to DB
- **WHEN** a NLQ question is submitted
- **THEN** an audit_logs record is created in the database with the question, generated SPARQL, and results

### Requirement: Audit log API uses database
The GET `/api/v1/audit-log` endpoint SHALL query the database for recent logs. It SHALL support optional query parameters: `tenantId`, `queryType`, `limit`, `offset`.

#### Scenario: Filter audit logs by tenant
- **WHEN** GET `/api/v1/audit-log?tenantId=university` is called
- **THEN** only logs for the "university" tenant are returned

#### Scenario: Filter audit logs by type
- **WHEN** GET `/api/v1/audit-log?queryType=NLQ` is called
- **THEN** only NLQ logs are returned

### Requirement: Audit log retention
The system SHALL support configurable audit log retention via `ontology.audit.retention-days` (default 90). A scheduled task SHALL run daily to delete records older than the retention period.

#### Scenario: Old logs are purged
- **WHEN** a log record is older than the configured retention days
- **THEN** the daily cleanup task deletes it

#### Scenario: Retention period is configurable
- **WHEN** `ontology.audit.retention-days` is set to 30 in application.yml
- **THEN** only logs from the last 30 days are retained

### Requirement: Clear audit logs still works
The POST `/api/v1/audit-log/clear` endpoint SHALL delete all audit log records from the database.

#### Scenario: Clear all logs
- **WHEN** POST `/api/v1/audit-log/clear` is called
- **THEN** all records in the audit_logs table are deleted
