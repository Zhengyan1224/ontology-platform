## MODIFIED Requirements

### Requirement: Naming convention configuration

The system SHALL support customizable naming conventions for OWL generation.

- Configuration SHALL be under `ontology.owl-generation.*`:
  - `name-case`: PascalCase or camelCase (default PascalCase)
  - `table-to-class-prefix`: optional prefix for class names
  - `column-to-property-prefix`: optional prefix for property names
  - `output-dir`: directory path for generated OWL files (was declared in YAML but not wired)
  - `enabled`: boolean flag to toggle OWL generation (default true)
- Plural table names SHALL be singularized by default (e.g., `books` → `Book`)
- Singularization SHALL correctly handle common irregular patterns: `ies` → `y`, `ses` → `s`, `sses` → `ss`, trailing `s` → strip
- Primary key columns SHALL be marked as `owl:FunctionalProperty` in the generated OWL

#### Scenario: Custom naming prefix

- **WHEN** `ontology.owl-generation.table-to-class-prefix` is set to `"Tbl"`
- **THEN** table `books` SHALL generate class `TblBook`

#### Scenario: Singularize statuses correctly (bug fix)

- **WHEN** a table is named `statuses`
- **THEN** the generated class SHALL be `Status` (not `Statuse`)
