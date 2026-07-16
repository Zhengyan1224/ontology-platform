# LLM Ontology Assist

## Purpose

Provide LLM-powered ontology assistance features including concept extraction from documents, concept proposal review workflows, and DDL-to-ontology hint generation, with REST and MCP interfaces.

## Requirements

### Requirement: Concept Extraction from Documents

System SHALL use the configured LLM to extract ontology concepts (classes, properties, relationships) from uploaded document content.

#### Scenario: Extract concepts from document
- **WHEN** ADMIN sends POST /tenants/{tenantId}/ontology/extract?documentId=xxx
- **THEN** system processes document chunks through LLM and returns 200 with proposed classes, properties, and relationships in JSON

#### Scenario: Extraction without a document
- **WHEN** ADMIN sends POST /tenants/{tenantId}/ontology/extract with raw text body
- **THEN** system returns 200 with extracted concepts from the provided text

### Requirement: Concept Proposal Review

System SHALL present extracted concepts as a proposed diff for human review before applying to the tenant's OWL.

#### Scenario: Review proposed concepts
- **WHEN** ADMIN sends GET /tenants/{tenantId}/ontology/proposals/{proposalId}
- **THEN** system returns 200 with the proposed additions in OWL format

#### Scenario: Apply proposed concepts
- **WHEN** ADMIN sends POST /tenants/{tenantId}/ontology/proposals/{proposalId}/apply
- **THEN** system merges the proposed concepts into the tenant OWL and returns 200 with the updated OWL

#### Scenario: Reject proposed concepts
- **WHEN** ADMIN sends POST /tenants/{tenantId}/ontology/proposals/{proposalId}/reject
- **THEN** system marks the proposal as rejected and returns 200

### Requirement: DDL-to-Ontology Hints

System SHALL suggest OWL annotations from database table/column comments and DDL metadata.

#### Scenario: Generate ontology hints from DDL
- **WHEN** ADMIN sends POST /tenants/{tenantId}/ontology/ddl-hints
- **THEN** system queries the tenant database for table/column comments and returns 200 with proposed OWL annotations

### Requirement: MCP Ontology Tools

System SHALL expose LLM ontology assist operations as MCP tools.

#### Scenario: MCP client proposes ontology additions
- **WHEN** MCP client calls ontology_extract tool with text or documentId
- **THEN** response includes proposed concepts

#### Scenario: MCP client applies a proposal
- **WHEN** MCP client calls ontology_apply_proposal tool with proposalId
- **THEN** response confirms the OWL was updated
