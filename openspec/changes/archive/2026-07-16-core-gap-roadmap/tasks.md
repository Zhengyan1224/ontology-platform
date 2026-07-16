## 1. Decision Rule Engine — Foundation

- [x] 1.1 Add rules and rule_history tables to init-content.sql
- [x] 1.2 Create Rule entity and RuleHistory entity
- [x] 1.3 Create RuleRepository and RuleHistoryRepository
- [x] 1.4 Create RuleService with CRUD + evaluate using SpEL
- [x] 1.5 Create RuleController with CRUD + evaluate endpoints
- [x] 1.6 Add rule management UI to tenant/index.html
- [x] 1.7 Add MCP rule tools (rule_list, rule_evaluate)
- [x] 1.8 Write tests for rule CRUD and evaluation

## 2. Decision Rule Engine — Triggers & Monitoring

- [x] 2.1 Implement automatic rule evaluation on SPARQL query results (optional trigger hook)
- [x] 2.2 Add rule execution history API (GET /rules/{id}/history)
- [x] 2.3 Add rule enable/disable toggle endpoint
- [x] 2.4 Write tests for history and trigger behavior

## 3. Action Workflow Engine — Actions

- [x] 3.1 Add actions table to init-content.sql
- [x] 3.2 Create Action entity and ActionRepository
- [x] 3.3 Create ActionService with CRUD + execute (sql_exec, api_call, notification)
- [x] 3.4 Implement SqlActionExecutor, ApiCallActionExecutor, NotificationActionExecutor
- [x] 3.5 Implement dry-run mode for sql_exec actions
- [x] 3.6 Create ActionController with CRUD + execute endpoints
- [x] 3.7 Add action management UI to tenant/index.html
- [x] 3.8 Add MCP action tools (action_list, action_execute)
- [x] 3.9 Write tests for action CRUD and execution

## 4. Action Workflow Engine — Workflows

- [x] 4.1 Add workflows table to init-content.sql
- [x] 4.2 Create Workflow entity and WorkflowRepository
- [x] 4.3 Create WorkflowService with CRUD + DAG validation (cycle detection via topological sort)
- [x] 4.4 Implement DAG executor with topological sort and step retry/timeout
- [x] 4.5 Create WorkflowController with CRUD + execute + validate endpoints
- [x] 4.6 Add workflow management UI to tenant/index.html (list, edit, run, validate)
- [x] 4.7 Add MCP workflow tools (workflow_list, workflow_execute)
- [x] 4.8 Write tests for workflow CRUD, cycle detection, and execution

## 5. Document Ingestion

- [x] 5.1 Add documents and document_chunks tables to init-content.sql
- [x] 5.2 Add Apache Tika dependency to pom.xml
- [x] 5.3 Create Document entity, DocumentChunk entity, and repositories
- [x] 5.4 Create DocumentIngestionService with upload, parse (Tika), chunk, embed pipeline
- [x] 5.5 Implement TF-IDF vector embedding (built-in, no external API needed)
- [x] 5.6 Implement in-memory cosine similarity search
- [x] 5.7 Create DocumentController with upload, query, list, delete, chunks endpoints
- [x] 5.8 Add document management UI to tenant/index.html (upload, list, search, view chunks)
- [x] 5.9 Add MCP document tools (document_list, document_query)
- [x] 5.10 Write tests (11 tests for upload, CRUD, search, empty file handling)

## 6. LLM Ontology Assist

- [x] 6.1 Create ontology_proposals table in init-content.sql
- [x] 6.2 Create OntologyProposal entity and repository
- [x] 6.3 Create LlmOntologyAssistService with extraction prompts (reuses NLQ LLM config)
- [x] 6.4 Implement DDL-to-ontology hints service (JDBC metadata → tables/columns/FKs)
- [x] 6.5 Create OntologyAssistController with extract, ddl-hints, generate-from-ddl, proposals CRUD, apply, reject endpoints
- [x] 6.6 Implement proposal merge logic (append to existing tenant_content + engine reinit)
- [x] 6.7 Add ontology assist UI to tenant/index.html (extract form, DDL hints, proposals list)
- [x] 6.8 Add MCP tools (ontology_extract, ontology_apply_proposal)
- [x] 6.9 Write tests (15 tests for extraction, proposals CRUD, apply/reject, DDL hints)

## 7. Integration & Polish

- [x] 7.1 Verify API key security — all new endpoints under `/api/v1/tenants/...` are NOT in public paths, covered by ApiKeyFilter (consistent with existing endpoints)
- [x] 7.2 Swagger auto-discovers all new `@RestController` endpoints under the existing `ontology-platform` group; no code changes needed
- [x] 7.3 Full test suite: 233 tests pass (207 existing + 11 doc + 15 ontology assist), zero regressions
- [x] 7.4 MCP conformance: 15 tools registered (tenant_list/info, sparql_query, sql_query, nlq_query, rule_list/evaluate, action_list/execute, workflow_list/execute, document_list/query, ontology_extract/apply_proposal) + 1 resource + 2 resource templates; server capabilities: tools=true, resources=false+true
