## ADDED Requirements

### Requirement: External prompt template
The LLM prompt SHALL be loaded from an external file rather than hardcoded in Java.

- Prompt template file SHALL be located at `src/main/resources/nlq-templates/prompt-template.txt`
- The template SHALL support the following placeholders:
  - `{{tenantId}}` — current tenant identifier
  - `{{schema}}` — ontology schema description (from `OntologySchemaProvider`)
  - `{{question}}` — user's natural language question
  - `{{examples}}` — few-shot example questions and their SPARQL queries
- If no template file exists, the system SHALL use a built-in default prompt

#### Scenario: Prompt loads from external file
- **WHEN** `prompt-template.txt` exists
- **THEN** the system SHALL use its content as the LLM prompt template
- **WHEN** the file contains `{{question}}`
- **THEN** the system SHALL replace it with the user's question before sending to LLM

### Requirement: Few-shot examples
The system SHALL include few-shot example SPARQL queries in the LLM prompt to guide generation quality.

- Examples SHALL be loaded from `nlq-templates/{tenantId}-examples.yml`
- Each example SHALL contain: `question` (string) and `sparql` (string)
- At least 3 examples per tenant
- Examples SHALL be tenant-specific

#### Scenario: Examples included in prompt
- **WHEN** LLM is invoked for the "sample" tenant
- **WHEN** `sample-examples.yml` exists with 3 examples
- **THEN** the prompt SHALL contain all 3 examples in the `{{examples}}` section

### Requirement: Structured schema description
The schema description in the prompt SHALL include explicit prefix declarations and class hierarchy.

- The schema section SHALL include: PREFIX declarations, class names with parent classes, property names with domain/range
- Format SHALL use a clear machine-readable structure (not free text)

#### Scenario: Schema includes hierarchy
- **WHEN** the ontology has `Professor ⊑ Employee ⊑ Person`
- **THEN** the prompt schema SHALL list: `:Person`, `  :Employee`, `    :Professor`

### Requirement: Output format constraint
The prompt SHALL instruct the LLM to return only valid SPARQL without explanation, wrapped in a code fence.

#### Scenario: LLM returns formatted SPARQL
- **WHEN** the LLM responds
- **THEN** the system SHALL extract SPARQL from markdown code fences if present
- **THEN** the system SHALL validate the extracted SPARQL contains at least SELECT or CONSTRUCT
