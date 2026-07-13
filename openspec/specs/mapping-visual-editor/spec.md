# Mapping Visual Editor

## Purpose

Provide a visual editor UI and API for viewing, editing, and applying OWL/OBDA mapping configurations generated from database schemas.

## Requirements

### Requirement: Draft response includes editable config

The system SHALL return a structured `editableConfig` field in the Draft response, representing the current mapping as an editable data structure.

- The `editableConfig` SHALL contain:
  - `tables`: array of `TableMapping` objects
  - `relationships`: array of `RelationshipMapping` objects
- Each `TableMapping` SHALL have: `tableName`, `className`, `classNameSuggested`, `iriTemplate`, `expose`, `columns`
- Each `ColumnMapping` SHALL have: `columnName`, `propertyName`, `propertyNameSuggested`, `isPk`, `isFk`, `expose`
- Each `RelationshipMapping` SHALL have: `fkTable`, `fkColumn`, `pkTable`, `objectPropertyName`, `objectPropertyNameSuggested`, `expose`
- `classNameSuggested` and `propertyNameSuggested` SHALL be populated from LLM review when available, else equal to current value

#### Scenario: Draft includes editable config structure
- **WHEN** a POST request is sent to `/tenants/{id}/mapping-assistant/draft`
- **THEN** the response SHALL contain `editableConfig` with `tables` and `relationships`

### Requirement: Config update endpoint

The system SHALL accept user-edited mapping configuration and regenerate OWL/OBDA accordingly.

- Endpoint: `PUT /tenants/{id}/mapping-assistant/config`
- Request body SHALL contain partial or full `TableMapping[]` and `RelationshipMapping[]`
- Backend SHALL merge received config into runtime `OwlGenerationProperties` overrides
- Backend SHALL regenerate OWL and OBDA with updated config
- Response SHALL be a full DraftResponse (same as POST draft) with new generated content

#### Scenario: Apply edited config returns regenerated draft
- **WHEN** a PUT request with edited `TableMapping[]` is sent to `/tenants/{id}/mapping-assistant/config`
- **THEN** the response SHALL contain updated OWL/OBDA reflecting the edited config

#### Scenario: Partial update preserves unchanged fields
- **WHEN** the request only includes `className` changes for one table
- **THEN** other tables SHALL retain their existing mapping

### Requirement: LLM suggests structured naming

The LLM review SHALL include structured JSON suggestions for class names, property names, and column exclusions.

- LLM prompt SHALL instruct the model to output a JSON code block with `suggestions` and `hideColumns`
- `suggestions` SHALL map table names to `{ className, columns: { columnName: { propertyName } } }`
- `hideColumns` SHALL list `tableName.columnName` strings to hide
- If JSON parsing fails, the system SHALL gracefully fall back to text-only review

#### Scenario: LLM output includes JSON suggestions
- **WHEN** LLM review is generated
- **THEN** the review text SHALL contain an optional JSON code block with structured suggestions

### Requirement: Visual editing UI

The system SHALL provide a browser-based UI for viewing and editing mapping configurations.

- Layout SHALL be three-panel: left Schema tree, center vis-network graph, right LLM suggestion panel
- Schema tree SHALL render each table with expandable column list
- Each tree node SHALL display current name and allow in-place editing
- Columns SHALL have a toggle checkbox for expose/hide
- LLM suggestion panel SHALL show recommended changes with "采纳" (Accept) buttons
- "应用" (Apply) button SHALL send the current configuration to the update endpoint
- The vis-network graph SHALL refresh after configuration is applied
- No npm/build step SHALL be required; pure static HTML + CDN vis-network

#### Scenario: Schema tree renders editable table list
- **WHEN** a DraftResponse is received
- **THEN** the Schema tree SHALL display all tables with their columns and expose toggles

#### Scenario: Apply button triggers config update
- **WHEN** user clicks "应用" after editing
- **THEN** a PUT request SHALL be sent to `/tenants/{id}/mapping-assistant/config`
- **THEN** the UI SHALL update to reflect the regenerated mapping
