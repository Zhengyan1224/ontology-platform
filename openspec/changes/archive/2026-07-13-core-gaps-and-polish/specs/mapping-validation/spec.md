## ADDED Requirements

### Requirement: System SHALL validate generated OBDA mappings
The system SHALL provide server-side validation of generated OBDA mappings before they are downloaded or applied.

#### Scenario: Validate on demand via API
- **WHEN** a client sends GET `/api/v1/tenants/{tenantId}/mapping/validate`
- **THEN** the system SHALL generate the OBDA and return a validation result with `valid` (boolean), `errors` (list), and `warnings` (list)

#### Scenario: Valid mapping returns success
- **WHEN** the generated OBDA references only existing tables and columns in the tenant's JDBC database
- **THEN** the validation result SHALL have `valid: true` and an empty `errors` list

#### Scenario: Invalid table reference returns error
- **WHEN** the generated OBDA references a table that does not exist in the tenant's JDBC database
- **THEN** the validation result SHALL have `valid: false` and include the missing table name in `errors`

#### Scenario: Invalid column reference returns error
- **WHEN** the generated OBDA references a column that does not exist on a table
- **THEN** the validation result SHALL have `valid: false` and include the column/table in `errors`

### Requirement: Validation SHALL check IRI template format
The system SHALL verify that all target IRI templates use valid `{column}` placeholder syntax.

#### Scenario: Missing primary key column in IRI template
- **WHEN** a mapping's target IRI template references a column that is not in the source SELECT
- **THEN** the validation result SHALL include a warning

### Requirement: Validation SHALL be included in ZIP generation response
The `POST generate-mapping` endpoint SHALL include validation results in its response.

#### Scenario: ZIP response includes validation
- **WHEN** a client generates a mapping ZIP
- **THEN** the response SHALL include a `validation` field with the same structure as the validate endpoint
