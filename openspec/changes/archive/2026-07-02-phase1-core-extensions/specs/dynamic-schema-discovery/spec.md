## ADDED Requirements

### Requirement: OWL class and hierarchy discovery
The system SHALL parse any OWL file loaded for a tenant to extract all class IRIs and their subclass relationships. The result SHALL include the complete class hierarchy (e.g., Professor ⊑ Employee ⊑ Person).

#### Scenario: Parse university ontology
- **WHEN** the system discovers the schema for the "university" tenant
- **THEN** the schema includes classes Person, Employee, Professor, Department with hierarchy Professor ⊑ Employee ⊑ Person

#### Scenario: Parse books ontology
- **WHEN** the system discovers the schema for the "sample" tenant
- **THEN** the schema includes classes Author, Book, Edition with no subclass relationships

### Requirement: OWL property discovery
The system SHALL parse any OWL file loaded for a tenant to extract all datatype and object properties, their domain and range, and any subproperty relationships.

#### Scenario: Parse university properties
- **WHEN** the system discovers the schema for the "university" tenant
- **THEN** the schema includes properties name (Person→string), departmentName (Department→string), worksFor (Person→Department), headOf (Employee→Department, subPropertyOf worksFor)

### Requirement: OBDA mapping discovery
The system SHALL parse any OBDA file loaded for a tenant to extract mapping IDs, target RDF templates, and source SQL queries.

#### Scenario: Parse books OBDA
- **WHEN** the system discovers the schema for the "sample" tenant
- **THEN** the schema includes 5 mappings with their source tables (tb_affiliated_writers, tb_books, tb_edition, tb_authors)

### Requirement: Dynamic schema endpoint
The GET `/api/v1/tenants/{id}/schema` endpoint SHALL return the dynamically discovered schema as structured JSON, not hardcoded text. The response SHALL include classes (with hierarchy), properties (with domain/range), and mappings (with SQL source).

#### Scenario: Schema endpoint returns discovered data
- **WHEN** GET `/api/v1/tenants/{id}/schema` is called
- **THEN** the response contains classes, properties, and mappings parsed from the tenant's OWL and OBDA files

### Requirement: LLM prompt uses dynamic schema
The system SHALL use the dynamically discovered schema when building the LLM prompt for natural language queries, replacing the current hardcoded strings.

#### Scenario: New tenant gets LLM support automatically
- **WHEN** a new tenant is created via API and a NLQ query is made
- **THEN** the LLM prompt includes the tenant's dynamically discovered schema without any Java code change
