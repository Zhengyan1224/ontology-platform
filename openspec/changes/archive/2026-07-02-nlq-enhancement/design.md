## Context

当前 NLQ 实现：
- `NaturalLanguageQueryService` — 双策略：LLM（LangChain4j）→ 模板回退（`SparqlTemplateGenerator`）
- `SparqlTemplateGenerator` — 11 个硬编码 regex → SPARQL 映射，分 university / sample 两组
- LLM prompt 为硬编码字符串，无 few-shot、schema 格式松散
- 所有 NLQ 请求阻塞等待完整结果，超时时间默认 30s
- 无上下文管理，每次 NLQ 请求独立

## Goals / Non-Goals

**Goals:**
- 模板从 Java 代码外部化到 YAML，新租户无需改代码即可注册 NLQ 模板
- LLM prompt 结构优化（few-shot 示例 + schema 格式约束），提高 SPARQL 生成准确率
- SSE 端点支持 NLQ 流式输出（逐步推送 SPARQL、中间状态、最终结果）
- 会话级上下文管理，支持追问（引用前序问题与查询结果）

**Non-Goals:**
- 不涉及前端 UI 实现（仅提供 SSE 端点，前端自行消费）
- 不实现 LLM 模型切换（仍通过 `ontology.nlq.llm.*` 配置）
- 不实现模板热加载（重启生效即可）
- 不涉及 NLQ 结果的格式化输出（复用现有 `SparqlResultFormatter`）

## Decisions

### D1 — YAML 模板格式

每个租户对应一个 YAML 文件（`nlq-templates/{tenantId}.yml`），结构为规则列表：

```yaml
rules:
  - patterns:
      - "list.*(all\\s+)?employees?"
      - "who works here"
    sparql: |
      SELECT ?person ?name WHERE { ?person a :Employee . ?person :name ?name . }
    description: "List all employees"
  - patterns:
      - "who\\s+works?\\s+for\\s+(.+?)(\\?)?$"
    sparql: "SELECT ?person ?name WHERE { ?person :worksFor ?dept . ?dept :departmentName \"{1}\" . }"
    params:
      - group: 1
    description: "Find people working for a department"
```

参数捕获用 `{1}`, `{2}` 引用 regex group。无匹配时 fallback 到 LLM（如启用）或返回空。

### D2 — LLM Prompt 模板外部化

将 LLM prompt 从 Java 硬编码字符串移到 `nlq-templates/prompt-template.txt`，支持 `{{tenantId}}`、`{{schema}}`、`{{question}}`、`{{examples}}` 占位符。方便调优 prompt 无需重新编译。

### D3 — SSE 流式架构

新增 `GET /api/v1/tenants/{id}/nlq/stream?question=...&sessionId=...` 端点，返回 `text/event-stream`：

```
event: status
data: {"stage":"translating","message":"正在生成 SPARQL..."}

event: sparql
data: {"sparql":"SELECT ?person ?name WHERE {...}"}

event: result
data: {"variables":["person","name"],"results":[...],"executionTimeMs":1234}

event: complete
data: {}
```

使用 Spring 的 `SseEmitter` 实现。超时设为 60s（可配置）。

### D4 — 会话上下文管理

新增 `SessionManager` 组件（内存 Map + TTL 过期），每个 `sessionId` 存储：
- 最近 5 轮对话的 Q/A
- 最近一条 SPARQL 查询的 `SparqlQueryResult`
- 会话创建时间

追问时，将历史 Q/A 作为上下文注入 LLM prompt 或模板匹配逻辑。

### D5 — SparqlTemplateGenerator 保留为加载器

不删除原有类，改为：从 YAML 加载（如存在）→ 否则用旧硬编码逻辑 → 确保向后兼容。

## Risks / Trade-offs

- **[Session 内存膨胀]** 内存 Map 无限制增长 → 引入 TTL 过期（默认 30 分钟无访问自动清理），可配置最大会话数
- **[SSE 连接断开]** 客户端断连后服务端继续执行 → 使用 `SseEmitter.onCompletion` 和 `onTimeout` 回调取消正在执行的查询
- **[YAML 注入]** 用户可编写任意模板 YAML → 模板文件由管理员通过 `application.yml` 指定，不在 API 层面暴露写操作
- **[LLM prompt 泄露]** prompt 模板中可能包含提示泄漏风险 → 模板文件纳入版本管理，code review 控制
