## ADDED Requirements

### Requirement: External template configuration
The system SHALL support NLQ templates defined in external YAML files, one per tenant.

- Template files SHALL be located at `src/main/resources/nlq-templates/{tenantId}.yml`
- If no template file exists for a tenant, the system SHALL fall back to the hardcoded templates in `SparqlTemplateGenerator`
- Template files SHALL be loaded at application startup and cached in memory

### Requirement: Template rule syntax
Each YAML template file SHALL support multiple rules with the following structure:

- `patterns`: List of regex patterns to match against the user's question
- `sparql`: The SPARQL query template string. Supports `{1}`, `{2}` placeholders referencing regex capture groups
- `description` (optional): Human-readable description of the rule

The system SHALL match rules in order and return the first match.

#### Scenario: Template loads and matches
- **WHEN** a user sends NLQ "list all employees" to the university tenant
- **WHEN** the tenant's YAML template contains a rule with pattern `list.*all\s+employees?`
- **THEN** the system SHALL return the SPARQL defined in the matching rule

#### Scenario: No match falls back to hardcoded templates
- **WHEN** a template YAML file exists but no rule matches the user's question
- **WHEN** `SparqlTemplateGenerator` has a hardcoded fallback for this tenant
- **THEN** the system SHALL use the hardcoded template

#### Scenario: No match and no hardcoded fallback
- **WHEN** no YAML rule matches and no hardcoded template exists
- **WHEN** LLM is configured and available
- **THEN** the system SHALL delegate to the LLM
- **WHEN** LLM is not available
- **THEN** the system SHALL throw an appropriate exception

### Requirement: Tenant-level override
When a YAML template file exists for a tenant, it SHALL completely replace (not merge with) the hardcoded templates for that tenant.

#### Scenario: YAML replaces hardcoded
- **WHEN** a tenant has both a YAML template file and hardcoded templates
- **THEN** only the YAML-defined rules SHALL be used for matching
- **WHEN** no YAML rule matches
- **THEN** `SparqlTemplateGenerator` SHALL NOT be consulted for this tenant
