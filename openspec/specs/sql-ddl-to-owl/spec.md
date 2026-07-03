# SQL DDL to OWL Generation

## Purpose

Generate a preliminary OWL ontology from a database schema by reading INFORMATION_SCHEMA via JDBC, reducing the cost of onboarding new tenants.

## Requirements

### Requirement: OWL generation from SQL DDL

The system SHALL generate a preliminary OWL ontology from a database schema.

- Endpoint: `POST /api/v1/tenants/{tenantId}/generate-owl`
- The endpoint SHALL read the database `INFORMATION_SCHEMA` for the tenant's configured JDBC connection
- Mapping rules:
  - Each table SHALL become an OWL class (e.g., `books` → `:Book`)
  - Each column SHALL become a data property (e.g., `title` → `:title`)
  - Each foreign key SHALL become an object property (e.g., `author_id` → `:writtenBy`)
  - Primary key columns SHALL be marked as `owl:FunctionalProperty` where appropriate
- The generated OWL SHALL be returned as file content in the response

#### Scenario: OWL generated from DB schema
- **WHEN** an admin sends POST to `/api/v1/tenants/{tenantId}/generate-owl`
- **THEN** the response SHALL contain a Turtle-format OWL ontology generated from the DB schema

#### Scenario: Empty DB returns minimal ontology
- **WHEN** the database has no user tables
- **THEN** the generated OWL SHALL contain only the `owl:Ontology` header

#### Scenario: Foreign key generates object property
- **WHEN** a table has a foreign key constraint referencing another table
- **THEN** the generated OWL SHALL include an object property connecting the two table classes

### Requirement: Naming convention configuration

The system SHALL support customizable naming conventions for OWL generation.

- Configuration SHALL be under `ontology.owl-generation.*`:
  - `name-case`: PascalCase or camelCase (default PascalCase)
  - `table-to-class-prefix`: optional prefix for class names
  - `column-to-property-prefix`: optional prefix for property names
- Plural table names SHALL be singularized by default (e.g., `books` → `Book`)

#### Scenario: Custom naming prefix
- **WHEN** `ontology.owl-generation.table-to-class-prefix` is set to `"Tbl"`
- **THEN** table `books` SHALL generate class `TblBook`
