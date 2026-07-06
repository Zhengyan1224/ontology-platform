# OBDA Mapping Generation

## Purpose

Automatically generate Ontop OBDA mapping files from database schema metadata (JDBC), eliminating manual authoring and ensuring OWL/OBDA consistency.

## ADDED Requirements

### Requirement: OBDA generation from SQL DDL

The system SHALL generate an OBDA mapping file from a database schema.

- Endpoint: `POST /api/v1/tenants/{tenantId}/generate-mapping`
- The endpoint SHALL return both the generated OWL and OBDA as files in a ZIP response
- The endpoint SHALL read the database `INFORMATION_SCHEMA` via JDBC `DatabaseMetaData`
- Mapping rules:
  - Each table SHALL become a class mapping with IRI template `:{prefix}{iri-separator}{pk-value}`
  - Each non-FK column SHALL become a datatype property in the class mapping
  - Each FK column SHALL become an object property mapping (either inline if per-table style, or separate mapping if per-element style)
- Join tables (tables whose ALL columns are part of FK composite keys) SHALL be handled per `join-table-behavior` config
- The generated OBDA SHALL be valid for Ontop 5.x consumption

#### Scenario: OBDA generated from DB schema

- **WHEN** an admin sends POST to `/api/v1/tenants/{tenantId}/generate-mapping`
- **THEN** the response SHALL contain a ZIP with `.owl` and `.obda` files generated from the DB schema

#### Scenario: Foreign key generates object property mapping

- **WHEN** a table has a foreign key referencing another table
- **THEN** the generated OBDA SHALL include an object property mapping between the two tables

#### Scenario: Primary key used as IRI template

- **WHEN** a table has a primary key column `id`
- **THEN** the IRI template for that table SHALL use the PK column as identifier (e.g., `:table/{id}`)

#### Scenario: Join table handled per configuration

- **WHEN** a table consists entirely of FK columns (composite foreign keys only)
- **THEN** the generated OBDA SHALL follow `ontology.owl-generation.join-table-behavior`:
  - `object-only` (default): skip class mapping, generate only object property mappings
  - `skip`: skip the table entirely
  - `class-and-object`: generate both class mapping and object property mappings

### Requirement: IRI template configuration

The system SHALL support customizable IRI template style for OBDA mapping generation.

- Configuration SHALL be under `ontology.owl-generation.*`:
  - `iri-template`: IRI template pattern (default `/{pk}`), also supports `-{pk}`
- The IRI template SHALL be appended to the class prefix to form the IRI
- Example: `iri-template: /{pk}` → `:author/{wr_code}`, `iri-template: -{pk}` → `:emp-{emp_code}`

#### Scenario: Default IRI template

- **WHEN** `ontology.owl-generation.iri-template` is not configured
- **THEN** the default IRI template `/{pk}` SHALL be used

#### Scenario: Custom IRI template

- **WHEN** `ontology.owl-generation.iri-template` is set to `-{pk}`
- **THEN** the OBDA mapping SHALL use hyphen-separated IRIs like `:emp-{emp_code}`

### Requirement: Unified generation endpoint

The system SHALL provide a unified endpoint that generates both OWL and OBDA simultaneously.

- Endpoint: `POST /api/v1/tenants/{tenantId}/generate-mapping`
- The endpoint SHALL delegate to both `OwlGeneratorService` and `ObdaGeneratorService`
- Response SHALL be a ZIP file containing:
  - `{tenantId}.owl` — generated OWL ontology (Turtle format)
  - `{tenantId}.obda` — generated OBDA mapping file
- The existing `POST /api/v1/tenants/{tenantId}/generate-owl` endpoint SHALL be marked as deprecated

#### Scenario: Unified endpoint returns ZIP

- **WHEN** an admin sends POST to `/api/v1/tenants/{tenantId}/generate-mapping`
- **THEN** the response SHALL be an `application/zip` containing both `.owl` and `.obda` files
