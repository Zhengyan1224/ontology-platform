## Why

Current platform is a semantic data access layer (SPARQL→SQL) — it can query ontology-backed data but cannot reason over business rules, execute decisions, or take actions. Report analysis shows three critical gaps blocking the path from "semantic query" to "ontology intelligence hub": no decision layer, no action layer, and limited semantic coverage.

## What Changes

- **Decision Layer**: Business rule engine with ontology-aware rule evaluation, consistency checking, and decision tracing
- **Action Layer**: Action definitions (API call, SQL exec, notification), workflow/DAG engine for action chaining, and execution service
- **Semantic Layer Enhancement**: Multi-source data ingestion (PDF, Word, docs), LLM-assisted ontology building from unstructured content, and vector-based semantic retrieval

## Capabilities

### New Capabilities
- `decision-rule-engine`: Define, store, and evaluate business rules that reference ontology concepts; rule triggers and decision traceability
- `action-workflow-engine`: Define actions (API call, SQL mutation, notification) and compose them into executable workflows with DAG orchestration
- `document-ingestion`: Ingest PDF, Word, and other unstructured documents, extract text, chunk, and store with vector embeddings
- `llm-ontology-assist`: LLM-powered extraction of classes, properties, and relationships from documents/schema to bootstrap or extend OWL ontologies

### Modified Capabilities
- *(none — all capabilities are new)*

## Impact

- New services: `RuleEngineService`, `ActionExecutionService`, `WorkflowOrchestrator`, `DocumentIngestionService`, `LlmOntologyAssistService`
- New persistence: rules table, action definitions table, workflow DAG table, document chunks + vector store
- New controllers/endpoints: rule CRUD + eval, action/workflow CRUD + execute, document upload + ingest, LLM-assisted modeling
- New dependencies: vector DB lib (pgvector or in-process), document parser lib (Apache Tika or similar), Drools or simple rule engine
- MCP server updated with new tools for rules, actions, and document queries
