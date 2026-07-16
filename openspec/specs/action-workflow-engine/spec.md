# Action Workflow Engine

## Purpose

Provide an action and workflow engine that enables ADMIN users to define reusable actions (SQL execution, API calls, notifications), compose them into DAG workflows, and execute them with dry-run support, exposed via REST and MCP tools.

## Requirements

### Requirement: Action Definition

System SHALL allow ADMIN users to define reusable actions of types: sql_exec, api_call, notification.
Each action SHALL have type-specific config stored as JSON.

#### Scenario: Create SQL action
- **WHEN** ADMIN sends POST /tenants/{tenantId}/actions with {type: "sql_exec", config: {sql: "UPDATE books SET price = price * 1.1 WHERE category = 'Rare'"}}
- **THEN** system returns 201 with the created action

#### Scenario: Create API call action
- **WHEN** ADMIN sends POST /tenants/{tenantId}/actions with {type: "api_call", config: {url: "https://example.com/webhook", method: "POST", bodyTemplate: "..."}}
- **THEN** system returns 201 with the created action

### Requirement: Action Execution

System SHALL execute an individual action by ID with optional dry-run mode.

#### Scenario: Execute SQL action in dry-run
- **WHEN** ADMIN sends POST /tenants/{tenantId}/actions/{actionId}/execute?dryRun=true
- **THEN** system returns 200 with the generated SQL and affected row count preview (without executing)

#### Scenario: Execute SQL action for real
- **WHEN** ADMIN sends POST /tenants/{tenantId}/actions/{actionId}/execute?dryRun=false
- **THEN** system executes the SQL and returns 200 with execution result

### Requirement: Workflow Definition

System SHALL allow ADMIN to define workflows as DAGs of action nodes with edges.

#### Scenario: Create a workflow
- **WHEN** ADMIN sends POST /tenants/{tenantId}/workflows with valid DAG JSON
- **THEN** system returns 201 with the created workflow

#### Scenario: Reject cyclic workflow
- **WHEN** ADMIN sends POST with DAG containing a cycle
- **THEN** system returns 400 with cycle detection error

### Requirement: Workflow Execution

System SHALL execute a workflow by topologically sorting nodes and running them in sequence (parallel for level-zero nodes).

#### Scenario: Execute workflow successfully
- **WHEN** ADMIN sends POST /tenants/{tenantId}/workflows/{workflowId}/execute
- **THEN** system runs all steps and returns 200 with per-step results and overall status

#### Scenario: Execute with step failure
- **WHEN** one action in the workflow fails
- **THEN** system marks the workflow as failed, returns partial results, and does not execute downstream steps

### Requirement: MCP Tools

System SHALL expose action and workflow operations as MCP tools.

#### Scenario: MCP client lists actions
- **WHEN** MCP client sends tools/list request
- **THEN** response includes action_list, action_execute, workflow_list, workflow_execute tools

#### Scenario: MCP client executes an action
- **WHEN** MCP client calls action_execute tool with actionId and dryRun params
- **THEN** system returns execution result
