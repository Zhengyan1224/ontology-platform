# SQL DDL to OWL Generation

## MODIFIED Requirements

### Requirement: OWL generation from SQL DDL

The system SHALL generate a preliminary OWL ontology from a database schema.

- Endpoint: `POST /api/v1/tenants/{tenantId}/generate-owl` (deprecated, use `POST /tenants/{id}/generate-mapping` or `PUT /tenants/{id}/mapping-assistant/config`)
- The endpoint SHALL read the database `INFORMATION_SCHEMA` for the tenant's configured JDBC connection
- Mapping rules:
  - Each table SHALL become an OWL class (e.g., `books` → `:Book`)
  - Each column SHALL become a data property (e.g., `title` → `:title`)
  - Each foreign key SHALL become an object property (e.g., `author_id` → `:writtenBy`)
  - Primary key columns SHALL be marked as `owl:FunctionalProperty` where appropriate
- User-provided overrides via `PUT /tenants/{id}/mapping-assistant/config` SHALL take precedence over automatic naming rules
- The generated OWL SHALL be returned as file content in the response

#### Scenario: OWL generated from DB schema with user overrides
- **WHEN** an admin sends PUT to `/tenants/{id}/mapping-assistant/config` with a custom `className`
- **THEN** the generated OWL SHALL use the custom `className` instead of the automatic name
