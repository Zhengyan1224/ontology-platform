## ADDED Requirements

### Requirement: Rule CRUD
System SHALL allow ADMIN users to create, read, update, and delete business rules for a tenant.
Each rule SHALL have: name, description, condition (SpEL expression), action references, and enabled status.

#### Scenario: Create a rule
- **WHEN** ADMIN sends POST /tenants/{tenantId}/rules with valid JSON body
- **THEN** system returns 201 with the created rule object

#### Scenario: List rules for a tenant
- **WHEN** ADMIN or DEV sends GET /tenants/{tenantId}/rules
- **THEN** system returns 200 with array of rules

#### Scenario: Non-ADMIN cannot create rules
- **WHEN** READONLY user sends POST /tenants/{tenantId}/rules
- **THEN** system returns 403 Forbidden

### Requirement: Rule Evaluation
System SHALL evaluate a rule's SpEL condition against a given ontology entity context and return pass/fail with trace info.

#### Scenario: Evaluate rule that passes
- **WHEN** ADMIN sends POST /tenants/{tenantId}/rules/{ruleId}/evaluate with context {price: 150, category: "Reference"}
- **THEN** system returns 200 with {passed: true, trace: [...]}

#### Scenario: Evaluate rule that fails
- **WHEN** context does not match rule condition
- **THEN** system returns 200 with {passed: false, trace: ["price 50 <= threshold 100"]}

### Requirement: Rule Monitoring
System SHALL record rule evaluation results with timestamp and context for audit.

#### Scenario: Admin queries rule execution history
- **WHEN** ADMIN sends GET /tenants/{tenantId}/rules/{ruleId}/history
- **THEN** system returns 200 with paginated list of evaluation records
