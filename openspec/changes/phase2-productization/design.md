## Context

Phase 1 实现了多租户管理、动态 Schema 发现和持久化审计日志。当前状态：
- **无认证**: 所有 API 完全公开，包括敏感操作（reinit、清空审计日志、租户 CRUD）
- **SPARQL 单一格式**: 仅返回 JSON，不支持标准 SPARQL 协议格式协商，不支持 CONSTRUCT 查询
- **NLQ 硬编码**: 12 个 regex 模板硬编码在 Java 代码中，LLM prompt 无示例，不支持流式或多轮对话

基于现有的 Spring Boot 3.4.3 + RDF4J 5.1.4 + Ontop 5.5 栈进行扩展。

## Goals / Non-Goals

**Goals:**
- 添加 API Key 认证保护所有非公开端点
- 支持 SPARQL 标准结果格式（XML/CSV/TSV/JSON-LD）和 CONSTRUCT 查询的 RDF 序列化（Turtle/RDF/XML/JSON-LD）
- 将 NLQ 模板改为 YAML 配置驱动，优化 LLM prompt，添加 SSE 流式响应和会话上下文

**Non-Goals:**
- 用户管理、角色权限（RBAC）、OAuth/JWT — 这些留在未来 Phase
- SPARQL UPDATE/INSERT/DELETE 操作
- NLQ 结果的流式查询执行（仅 SPARQL 生成流式，查询执行仍为同步）
- 会话持久化（仅内存，重启丢失）

## Decisions

### D1: API Key 认证而非 JWT/OAuth
- **选择**: `X-API-Key` header + Spring Security `OncePerRequestFilter`
- **原因**: 系统是内部平台而非面向互联网，API Key 实现简单、可配置、无需用户登录流程。JWT/OAuth 可在 Phase 后续版本添加。
- **替代方案**: JWT — 增加了 token 签发、刷新、撤销的复杂度，目前不需要

### D2: YAML 模板配置而非 JSON
- **选择**: YAML 配置文件 `src/main/resources/nlq-templates/{tenantId}.yml`
- **原因**: 与 Spring Boot 配置风格一致，支持注释，可读性好，便于版本管理
- **替代方案**: DB 存储 — 模板变更频率极低，文件配置更简单

### D3: SSE 流式而非 WebSocket
- **选择**: SSE (Server-Sent Events) 通过 `text/event-stream`
- **原因**: 单向服务器推送场景（LLM 响应流），SSE 原生支持浏览器 EventSource API，比 WebSocket 简单。NLQ 不需要客户端→服务器的流式。
- **替代方案**: WebSocket — 双向通信，复杂度高，当前不需要

### D4: 会话 ID 通过 Header 传递而非 Cookie
- **选择**: `X-Session-Id` header
- **原因**: 平台以 API 使用为主（程序化调用），header 方式对 REST 客户端和浏览器都友好，无需处理 Cookie 的安全问题

### D5: 使用 RDF4J Rio 序列化 SPARQL 结果
- **选择**: 利用已有依赖 `rdf4j-rio-turtle`、`rdf4j-rio-ntriples`、`rdf4j-rio-rdfxml` 进行格式转换
- **原因**: 这些依赖已在 `pom.xml` 中（通过 Ontop/RDF4J 传递引入），无需新增依赖。Rio 是 RDF4J 的标准序列化库，支持所有目标格式
- **替代方案**: 手工构建 XML/CSV — 容易出错，不符合标准

### D6: CONSTRUCT 使用 GraphQuery API
- **选择**: `conn.prepareGraphQuery()` 处理 CONSTRUCT/DESCRIBE，`conn.prepareTupleQuery()` 处理 SELECT
- **原因**: RDF4J 原生支持两种查询类型区分，GraphQuery 返回 `org.eclipse.rdf4j.model.Model`，可通过 Rio 序列化为任意 RDF 格式

### D7: NLQ 模板提供 file 格式（`name`, `pattern`, `sparql`, `description`）每个 template 一条记录
```
- name: "list books"
  pattern: "list.*(all\\s+)?books?"
  sparql: "SELECT ?book ?title WHERE { ?book a :Book . ?book :title ?title . }"
  description: "列出所有书籍"
```
This allows easy customisation by end users without touching Java code.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                Phase 2 Architecture Overview                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Security Filter Chain                    │   │
│  │  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  │   │
│  │  │ PublicPaths │→│ ApiKeyFilter │→│ Controller │  │   │
│  │  │  (skip)     │  │ (401/403)    │  │            │  │   │
│  │  └─────────────┘  └──────────────┘  └────────────┘  │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              SPARQL Format Negotiation                │   │
│  │                                                      │   │
│  │  Accept → ┌────────────────┐                         │   │
│  │           │ ContentNegotia-│ → JSON / XML / CSV      │   │
│  │           │ tionStrategy   │ → TSV / Turtle / LD     │   │
│  │           └────────────────┘                         │   │
│  │                                                      │   │
│  │  GraphQuery ──→ Rio Writer ──→ Turtle / RDF/XML     │   │
│  │  TupleQuery ──→ SPARQLResultsWriter ──→ XML/CSV/TSV │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              NLQ Enhancement Layer                    │   │
│  │                                                      │   │
│  │  YAML Template Loader → TemplateMatcher              │   │
│  │  LLM Prompt Builder (few-shot + schema + history)    │   │
│  │  SessionManager (ConcurrentHashMap, 30min TTL)       │   │
│  │  SseEmitter Controller (stream LLM → SPARQL → exec) │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Risks / Trade-offs

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 内存会话丢失 | 重启后用户需重新开始对话 | 说明文档明确标注，Postman/前端可感知 |
| SSE 连接超时 | LLM 响应慢时连接断开 | 配置长超时（如 5 分钟），使用心跳事件 |
| API Key 泄露 | 恶意使用平台资源 | 建议通过环境变量注入，生产环境使用密钥管理服务 |
| YAML 模板格式错误 | 租户 NLQ 不可用 | 启动时校验模板文件格式，失败时日志警告并回退 LLM |
| CONSTRUCT 大结果集 | 内存溢出 | 限制结果集大小（默认 1000 条），可配置 |
