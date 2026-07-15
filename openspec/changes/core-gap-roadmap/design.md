## Context

Platform currently serves as an OBDA semantic layer — SPARQL→Ontop→SQL→RDB. Three priority gaps identified via report comparison:

- **Decision Layer**: No rule engine to evaluate business logic against ontology concepts
- **Action Layer**: No ability to execute operations beyond read queries
- **Semantic Coverage**: Only RDB sources; no document ingestion or LLM-assisted building

All three are independent in deployment but integrate at the ontology and tenant level.

## Goals / Non-Goals

**Goals:**
- Business rule engine that evaluates rules referencing OWL classes/properties, with full traceability
- Action definition model + DAG workflow executor for chaining actions
- Document ingestion pipeline (PDF, Word) with vector embeddings
- LLM-assisted extraction of ontology concepts from documents/DDL
- All capabilities exposed via REST API + MCP tools

**Non-Goals:**
- Not building a general-purpose BPMN workflow engine — DAG-only for action chaining
- Not replacing Ontop — OBDA stays as the primary structured data access path
- Not building a full graph database — vector store supplements, not replaces
- Not supporting streaming/IoT data sources in this phase

## Decisions

### D1: Rule Engine — Spring Expression Language (SpEL) over Drools
- **Why**: Drools adds ~15MB dependency and complex DSL. SpEL is already on classpath via Spring, integrates naturally with our POJO model, and is sufficient for ontology-referencing rules like `if book.price > 100 and book.category == 'Reference' then ...`
- **Rule model**: JSON-serialized AST with condition tree + action references
- **When to reconsider**: If rule volumes exceed 10K or need forward-chaining inference

### D2: Workflow — Simple DAG engine, not BPMN
- **Why**: BPMN engines (Camunda, Flowable) are heavy. Our action chains are simple DAGs (steps A, B, then C). A lightweight topological-sort executor with step retry/timeout is sufficient.
- **Execution model**: Synchronous for short chains (<30s), async with webhook callback for long chains

### D3: Vector Store — pgvector on existing H2/PG
- **Why**: No new infrastructure. Postgres has pgvector; H2 can use in-process embedding matching via simple cosine-sim on arrays. Keeps operational overhead near zero.
- **Alternative rejected**: Dedicated vector DB (Qdrant, Milvus) — premature for current scale

### D4: Document Parsing — Apache Tika
- **Why**: Mature, supports PDF/DOCX/HTML, single dependency. LangChain4j document loader is another option but less flexible for chunking control.
- **Chunking**: Recursive character split with overlap, stored alongside embeddings

### D5: LLM Ontology Assist — Reuse existing NLQ LLM config
- **Why**: We already have `ontology.nlq.llm.api-key` and `OpenAiChatModel` configured. Extend with extraction prompts for ontology building — no new LLM infra needed.
- **Approach**: Few-shot prompt → extract JSON classes/properties/relations → validate against existing OWL → propose diff

### D6: Auth — Reuse existing API key/JWT model
- Rule/action/document CRUD requires ADMIN. Rule eval and document read require authenticated user (ADMIN or DEV).
- MCP tools inherit the same auth via existing ApiKeyFilter.

## Data Model Additions

```
rules:
  id            UUID PK
  tenant_id     VARCHAR FK → tenants
  name          VARCHAR
  description   TEXT
  condition     JSON      -- SpEL condition tree
  actions       JSON[]    -- action references to execute when triggered
  enabled       BOOLEAN
  created_at    TIMESTAMP

actions:
  id            UUID PK
  tenant_id     VARCHAR FK → tenants
  name          VARCHAR
  type          VARCHAR   -- sql_exec, api_call, notification
  config        JSON      -- type-specific config (sql, url, template)
  created_at    TIMESTAMP

workflows:
  id            UUID PK
  tenant_id     VARCHAR FK → tenants
  name          VARCHAR
  dag           JSON      -- {nodes: [{action_id, id}], edges: [{from, to}]}
  enabled       BOOLEAN
  created_at    TIMESTAMP

documents:
  id            UUID PK
  tenant_id     VARCHAR FK → tenants
  filename      VARCHAR
  mime_type     VARCHAR
  chunk_count   INT
  created_at    TIMESTAMP

document_chunks:
  id            UUID PK
  document_id   UUID FK → documents
  content       TEXT
  embedding     ARRAY FLOAT  -- pgvector / H2 array
  metadata      JSON
  created_at    TIMESTAMP
```

## Integration Points

```
                    ┌─────────────────┐
                    │   MCP Server    │
                    │ (existing)      │
                    │ + new tools     │
                    └────────┬────────┘
                             │
         ┌───────────────────┼───────────────────┐
         ▼                   ▼                   ▼
   ┌──────────┐      ┌──────────────┐    ┌──────────────┐
   │ Decision │      │   Action     │    │   Semantic   │
   │  Layer   │─────▶│   Layer      │    │ Enhancement  │
   │          │triggers│            │    │              │
   │ RuleEngine│     │ WorkflowExec│    │ DocIngestion │
   │ SpEL eval │     │ DAG engine  │    │ LLM Assist   │
   └──────────┘      └──────────────┘    └──────────────┘
        │                   │                   │
        ▼                   ▼                   ▼
   ┌─────────────────────────────────────────────────┐
   │           Existing Platform Services             │
   │  (EngineRegistry, CachedSparql, TenantPersistence)│
   └─────────────────────────────────────────────────┘
```

## Risks / Trade-offs

- **[Risk] SpEL limitations for complex rules** → Mitigation: rules can delegate to SPARQL queries; migrate to Drools if rule complexity grows beyond SpEL capacity
- **[Risk] pgvector not available in H2 default mode** → Mitigation: fall back to in-memory cosine similarity on Java arrays; document chunk count is small (<10K)
- **[Risk] LLM extraction quality** → Mitigation: always present extracted concepts as "proposed diff" requiring human review before applying to OWL
- **[Risk] Action execution side effects** → Mitigation: all actions are logged with before/after state snapshots; dry-run mode for testing
- **[Risk] DAG workflow deadlock** → Mitigation: cycle detection at definition time via topological sort; max 50 nodes per workflow
