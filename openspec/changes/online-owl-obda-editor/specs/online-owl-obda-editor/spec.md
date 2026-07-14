## ADDED Requirements

### Requirement: Generate OWL/OBDA from database metadata

The system SHALL provide an API endpoint to generate OWL and OBDA content from a tenant's JDBC database metadata, returning the generated content without saving it.

The generated OWL SHALL use Turtle format and include class definitions for each database table, object properties for foreign key relationships, and datatype properties for columns.

The generated OBDA SHALL include prefix declarations and mapping declarations for each table.

#### Scenario: Generate from DB metadata successfully

- **WHEN** user sends `POST /api/v1/tenants/{tenantId}/generate`
- **THEN** system connects to the tenant's JDBC database, reads table/column/FK metadata
- **THEN** system returns `{ owlContent: "...", obdaContent: "..." }` with generated Turtle and OBDA text
- **THEN** the generated content is NOT saved to the database

#### Scenario: Generate with non-existent tenant

- **WHEN** user sends `POST /api/v1/tenants/nonexistent/generate`
- **THEN** system returns 404 with error `TENANT_NOT_FOUND`

#### Scenario: Generate with invalid JDBC connection

- **WHEN** tenant's JDBC credentials are invalid
- **THEN** system returns 500 with error `MAPPING_GENERATION_FAILED`

### Requirement: Save OWL/OBDA content to database

The system SHALL provide an API endpoint to save OWL and OBDA content to the `tenant_content` table, associated with the specified tenant.

Previously saved content SHALL be replaced on save.

Saving content SHALL NOT trigger engine reinitialization.

#### Scenario: Save content successfully

- **WHEN** user sends `PUT /api/v1/tenants/{tenantId}/content` with body `{ owlContent: "...", obdaContent: "..." }`
- **THEN** system upserts the content into `tenant_content` table
- **THEN** system returns `{ status: "saved" }`

#### Scenario: Save content requires admin role

- **WHEN** non-admin user sends `PUT /api/v1/tenants/{tenantId}/content`
- **THEN** system returns 403 Forbidden

### Requirement: Apply saved OWL/OBDA content to engine

The system SHALL provide an API endpoint that saves the current editor content and reinitializes the Ontop engine for the tenant.

On apply, the system SHALL:
1. Save the content to `tenant_content` table
2. Write OWL content to a temporary `.ttl` file
3. Write OBDA content to a temporary `.obda` file
4. Destroy the existing Ontop engine
5. Create a new engine with the temp file paths
6. Evict SPARQL cache for the tenant

When `owlContent` or `obdaContent` is null/empty, the engine SHALL fall back to the original file paths from the tenant configuration.

#### Scenario: Apply content successfully

- **WHEN** user sends `POST /api/v1/tenants/{tenantId}/apply` with body `{ owlContent: "...", obdaContent: "..." }`
- **THEN** system saves content to `tenant_content` table
- **THEN** system writes temp files and rebuilds the engine
- **THEN** system returns `{ status: "applied", health: "UP" }`

#### Scenario: Apply with null content falls back to file paths

- **WHEN** user sends `POST /api/v1/tenants/{tenantId}/apply` with body `{ owlContent: null, obdaContent: null }`
- **THEN** system clears the `tenant_content` row
- **THEN** system reinitializes engine using the original OWL/OBDA file paths from tenant config

#### Scenario: Apply content requires admin role

- **WHEN** non-admin user sends `POST /api/v1/tenants/{tenantId}/apply`
- **THEN** system returns 403 Forbidden

### Requirement: View and edit OWL/OBDA content in web UI

The Tenant detail page SHALL display an OWL/OBDA editor panel with two textareas for editing OWL and OBDA content.

The editor panel SHALL provide three buttons:
- **Generate from DB**: Fills the textareas with freshly generated content from DB metadata
- **Save Draft**: Saves the current textarea content to the database
- **Save & Apply**: Saves and applies the content, then refreshes the tenant status

The admin Tenant list page SHALL include an "Edit Content" link to navigate directly to the editor.

#### Scenario: Generate from DB via UI

- **WHEN** user clicks "Generate from DB" button
- **THEN** system calls `POST /generate` and populates both textareas with the response content

#### Scenario: Save draft via UI

- **WHEN** user clicks "Save Draft" button
- **THEN** system calls `PUT /content` with current textarea values
- **THEN** a toast message "Saved" is displayed

#### Scenario: Save and apply via UI

- **WHEN** user clicks "Save & Apply" button
- **THEN** system calls `POST /apply` with current textarea values
- **THEN** a toast message "Applied" is displayed
- **THEN** the tenant health status is refreshed
