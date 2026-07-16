# Ontology Platform

基于 **Ontop** 的 OBDA（Ontology-Based Data Access）语义查询服务平台。

通过 OWL 本体和 OBDA 映射，把关系型数据库包装成 SPARQL、自然语言、GraphQL、MCP 可查询的语义访问层。数据不搬迁，查询时由 Ontop 将 SPARQL 实时改写为 SQL。

```text
业务数据库 + OWL 本体 + OBDA 映射
       ↓
 Ontology Platform
       ↓
 SPARQL / NLQ / GraphQL / MCP / SQL
```

---

## 功能总览

| 能力 | 说明 |
|------|------|
| **多租户** | 每个租户对应一个业务数据库 + OWL + OBDA，独立引擎实例 |
| **SPARQL 查询** | SELECT / CONSTRUCT / DESCRIBE / ASK，支持 JSON / XML / CSV / TSV / Turtle / RDF/XML / JSON-LD |
| **自然语言查询** | LLM 生成 SPARQL（OpenAI 兼容接口），失败回退模板规则；SSE 流式响应 |
| **GraphQL 接口** | `/graphql` 统一入口 |
| **联合查询** | 跨租户 SERVICE 查询，VALUES 子句重写合并 |
| **本体可视化** | OWL 类/属性关系图 |
| **SQL 执行** | 在租户数据库执行 SQL（SELECT/INSERT/UPDATE/DELETE），阻止 DROP/TRUNCATE/ALTER 等危险语句 |
| **OWL/OBDA 编辑器** | 在线编辑 OWL 和 OBDA 内容，保存后自动应用并重新初始化引擎 |
| **决策规则引擎** | SpEL 表达式规则，SPARQL 查询后自动触发评估，支持历史记录和开关 |
| **动作引擎** | 三种动作类型：SQL 执行 / HTTP API 调用 / 通知记录，支持 Dry-Run 预览 |
| **工作流引擎** | DAG 工作流编排（循环检测 + 拓扑排序执行），单工作流最多 50 节点 |
| **文档导入** | PDF/DOCX/HTML/TXT 解析（Apache Tika），TF-IDF 向量化，余弦相似度搜索 |
| **LLM 本体辅助** | 从自然语言描述或 DDL 元数据生成 OWL/OBDA 方案，支持评审 → 应用流程 |
| **MCP 协议** | Model Context Protocol（Streamable HTTP），15 个工具供 AI Agent 调用 |
| **API Key + JWT 鉴权** | 双因子认证，租户级 scope 控制，Bucket4j 速率限制 |
| **查询缓存** | Caffeine 缓存，按租户驱逐 |
| **审计日志** | 自动记录查询和鉴权事件 |
| **查询历史** | SPARQL 执行历史记录，支持浏览/重新执行/删除 |
| **保存查询** | 查询保存 + 分享 token |
| **OWL/OBDA 生成** | 基于 JDBC 元数据自动生成本体和映射草稿 |
| **Mapping Assistant** | LLM/规则驱动的 OWL/OBDA 草稿评审助手 |

---

## 架构

```
┌─────────────────────────────────────────────┐
│  客户端 / AI Agent / MCP Client             │
└──────┬──────────────────────────┬────────────┘
       │ HTTP / SSE               │ MCP
       ▼                          ▼
┌──────────────┐     ┌──────────────────────┐
│ REST API     │     │ MCP Streamable HTTP  │
│ :8080/api/v1 │     │ :8080/mcp            │
├──────────────┴─────┴──────────────────────┤
│            Spring Boot 3.4.3              │
├──────────────┬─────┬──────────────────────┤
│ SPARQL → SQL  │ NLQ  │ GraphQL            │
│ (Ontop 5.5)   │ LLM  │ (Spring GraphQL)   │
├──────────────┴─────┴──────────────────────┤
│         RDF4J 5.1.4 / Ontop Engine        │
├───────────────────────────────────────────┤
│  JDBC → 业务数据库 (H2 / PostgreSQL / ...) │
└───────────────────────────────────────────┘
```

### 核心技术栈

- **Spring Boot 3.4.3** — 应用框架
- **Ontop 5.5.0** — SPARQL → SQL 改写引擎
- **RDF4J 5.1.4** — RDF 模型和查询处理
- **LangChain4j 1.0.0-beta3** — LLM 集成（NLQ、Ontology Assist）
- **Apache Tika 3.1.0** — 文档解析（PDF/DOCX/HTML/TXT）
- **Spring Security** — API Key + JWT 鉴权
- **Caffeine** — 查询缓存
- **Bucket4j** — 速率限制
- **MCP SDK 2.0** — Model Context Protocol
- **H2 Database** — 默认平台库（文件模式）

---

## 快速启动

### 环境要求

| 依赖 | 版本 |
|------|------|
| Java | 21+ |
| Maven | 3.8+ |

### 开发模式启动

```bash
mvn spring-boot:run
# 或
mvn exec:java
```

服务默认在 `http://localhost:8080` 启动。

> `pom.xml` 中 `spring-boot-maven-plugin` 配置了 `<skip>true</skip>`，因此推荐使用 `spring-boot:run` 或 `exec:java`。

### 打包为可执行 JAR

```bash
mvn clean package spring-boot:repackage -DskipTests -Dspring-boot.repackage.skip=false
```

启动 JAR：

```bash
# Windows PowerShell
$env:ADMIN_PASSWORD="change-me"
$env:JWT_SECRET="change-me-change-me-change-me-change-me"
java -jar target\ontology-platform-1.0.0-SNAPSHOT.jar

# Linux/macOS
ADMIN_PASSWORD=change-me JWT_SECRET=change-me-change-me-change-me-change-me \
  java -jar target/ontology-platform-1.0.0-SNAPSHOT.jar
```

指定端口：

```bash
java -jar target/ontology-platform-1.0.0-SNAPSHOT.jar --server.port=8081
```

> H2 文件库默认路径 `./data/ontology-platform` 是相对于启动目录的，生产部署建议使用绝对路径。

### 运行测试

```bash
mvn test
```

测试使用 H2 内存库，不会污染本地数据文件。

---

## 配置说明

### 基础配置 `application.yml`

```yaml
server:
  port: 8080

ontology:
  auth:
    enabled: true
    api-keys:
      - admin-key-001    # 开发环境管理员密钥
      - dev-key-002      # 开发环境普通开发者密钥
    admin-password: ${ADMIN_PASSWORD:admin123}
    jwt:
      secret: ${JWT_SECRET:...}
  nlq:
    llm:
      api-key: ${LLM_API_KEY:sk-placeholder}
      model: gpt-4o-mini
      base-url: https://integrate.api.nvidia.com/v1
```

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `ADMIN_PASSWORD` | `admin123` | 管理员登录密码 |
| `JWT_SECRET` | 内置默认值 | JWT 签名密钥（至少 256 位） |
| `LLM_API_KEY` | `sk-placeholder` | OpenAI 兼容 API Key，设为 `sk-placeholder` 时 NLQ 回退模板模式 |

### 租户配置

内置两个示例租户：

```yaml
ontology:
  tenants:
    - id: sample
      name: Sample Books Database
      jdbc-url: jdbc:h2:file:./data/ontology-platform;...
      jdbc-driver: org.h2.Driver
      owl-path: ontologies/exampleBooks.owl
      obda-path: ontologies/exampleBooks.obda

    - id: university
      name: University Reasoning Demo
      ...
```

新增租户可在 `application.yml` 的 `ontology.tenants` 下添加，或通过 API 创建。

### 组件配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `ontology.cache.sparql.max-size` | 500 | SPARQL 结果缓存最大条目数 |
| `ontology.cache.sparql.ttl-seconds` | 300 | SPARQL 缓存 TTL |
| `ontology.rate-limit.capacity` | 20 | 令牌桶容量 |
| `ontology.rate-limit.refill-tokens` | 10 | 每次补充令牌数 |
| `ontology.rate-limit.refill-period-seconds` | 60 | 补充周期（秒） |
| `ontology.federated-query.timeout-ms` | 30000 | 联合查询超时 |
| `ontology.federated-query.max-concurrency` | 4 | 联合查询最大并发 |
| `ontology.sparql.max-results` | 10000 | SPARQL 查询结果上限 |
| `ontology.owl-generation.enabled` | true | OWL 自动生成开关 |
| `ontology.audit.retention-days` | 90 | 审计日志保留天数 |
| `ontology.query-history.retention-days` | 30 | 查询历史保留天数 |

---

## 平台使用流程

### 1. 首次启动

启动后，打开 `http://localhost:8080/` 进入首页。内置两个示例租户 `sample`（书籍）和 `university`（大学）。

### 2. 管理租户

**管理页面**：`http://localhost:8080/admin/`

操作入口：

| 操作 | 页面 / API |
|------|------------|
| 查看租户列表 | 首页 / `GET /api/v1/tenants` |
| 创建租户 | 管理页面 / `POST /api/v1/tenants` |
| 查看租户详情 | 管理页面点击租户 / `GET /api/v1/tenants/{id}` |
| 在线编辑 OWL/OBDA | 租户详情页 → OWL/OBDA Editor 卡片 |
| 重新初始化引擎 | 租户详情页 → Reinitialize Engine |
| 执行 SQL | 租户详情页 → SQL Query 卡片 |
| 删除租户 | 租户详情页 → Delete Tenant |

> 创建租户时可不包含 OWL/OBDA 文件，引擎会显示 `not_initialized` 健康状态。之后通过在线编辑器添加内容并 Save & Apply 即可初始化。

### 3. 执行 SPARQL 查询

**页面**：`http://localhost:8080/tenant/?id=sample`

```bash
curl -X POST "http://localhost:8080/api/v1/tenants/sample/sparql" \
  -H "X-API-Key: dev-key-002" \
  -H "Content-Type: application/sparql-query" \
  --data "PREFIX : <http://meraka/moss/exampleBooks.owl#> SELECT ?book ?title WHERE { ?book a :Book ; :title ?title . } LIMIT 5"
```

### 4. 自然语言查询

**页面**：`http://localhost:8080/nlq/`

```bash
curl -X POST "http://localhost:8080/api/v1/tenants/sample/nlq" \
  -H "X-API-Key: dev-key-002" \
  -H "Content-Type: application/json" \
  --data "{\"question\":\"列出所有书籍及其作者\"}"
```

配置有效的 `LLM_API_KEY` 后使用 LLM 生成 SPARQL；未配置时使用模板规则。

### 5. 管理业务规则

**页面**：租户详情页 → Rules 卡片

- 创建 SpEL 条件规则（如 `price > 100 and category == 'Reference'`）
- 为规则关联动作（引用 Action ID）
- 手动评估规则（传入 JSON 上下文）
- 查看评估历史和执行跟踪
- 规则会在 SPARQL 查询后自动触发评估

### 6. 创建和管理动作

**页面**：租户详情页 → Actions 卡片

三种动作类型：

| 类型 | 说明 | 配置示例 |
|------|------|----------|
| `sql_exec` | 执行 SQL（INSERT/UPDATE/DELETE） | `{"sql":"UPDATE books SET price = price * 1.1"}` |
| `api_call` | 调用外部 HTTP API | `{"url":"https://hook.example.com/notify","method":"POST"}` |
| `notification` | 记录通知日志 | `{"message":"Rule triggered"}` |

支持 Dry-Run 预览执行效果（不实际变更数据）。

### 7. 编排工作流

**页面**：租户详情页 → Workflows 卡片

- 以 DAG 格式定义工作流（JSON 节点 + 边）
- 自动循环检测（拓扑排序）
- 顺序执行已排序的节点，支持单步结果查看
- 最多 50 个节点

### 8. 文档导入与搜索

**页面**：租户详情页 → Documents 卡片

- 上传 PDF / DOCX / HTML / TXT 文件
- 自动解析 → 分块 → TF-IDF 向量化
- 语义相似度搜索（内置余弦相似度，无需外部向量数据库）
- 支持分块内容预览

### 9. LLM 本体辅助

**页面**：租户详情页 → Ontology Assist 卡片

| 功能 | 说明 |
|------|------|
| Extract Ontology | 输入业务描述（如"商品中心的商品和类目"），LLM 生成 OWL/OBDA 方案 |
| Get DDL Hints | 读取租户数据库 JDBC 元数据（表、列、主键、外键） |
| Generate from DDL | 基于 DDL 元数据生成 OWL/OBDA 方案（LLM 或规则回退） |
| Apply | 将提案合并到租户的 OWL/OBDA 内容中，并重新初始化引擎 |
| Reject | 拒绝提案（可选填写原因） |

### 10. MCP 协议（AI Agent 集成）

MCP 服务端地址：`http://localhost:8080/mcp`

内置 15 个工具：

| 工具 | 说明 |
|------|------|
| `tenant_list` | 列出所有租户及健康状态 |
| `tenant_info` | 获取租户详细信息 |
| `sparql_query` | 执行 SPARQL 查询 |
| `sql_query` | 执行 SQL 查询（SELECT 只读） |
| `nlq_query` | 自然语言转 SPARQL 查询 |
| `rule_list` | 列出租户业务规则 |
| `rule_evaluate` | 评估指定规则 |
| `action_list` | 列出可用动作 |
| `action_execute` | 执行动作（支持 dry-run） |
| `workflow_list` | 列出工作流 |
| `workflow_execute` | 执行工作流 |
| `document_list` | 列出已上传文档 |
| `document_query` | 语义搜索文档内容 |
| `ontology_extract` | 从描述提取本体概念 |
| `ontology_apply_proposal` | 应用本体提案 |

资源模板：

| URI | 说明 |
|-----|------|
| `tenants://health` | 租户健康状态 |
| `ontology://{tenantId}` | 租户 OWL 内容 |
| `ontology://{tenantId}/mapping` | 租户 OBDA 映射内容 |

### 11. 自动生成 OWL/OBDA

```bash
# 基于 JDBC 元数据生成 OWL/OBDA ZIP 包
curl -X POST "http://localhost:8080/api/v1/tenants/{tenantId}/generate-mapping" \
  -H "X-API-Key: admin-key-001"
```

生成的 ZIP 包含 `{tenantId}.owl` 和 `{tenantId}.obda` 文件。

### 12. Mapping Assistant 评审助手

**页面**：`http://localhost:8080/mapping-assistant/`

读取数据库元数据 → 生成可评审的 OWL/OBDA 草稿 → LLM 或规则输出命名建议、风险点、人工确认清单。

不会自动写入文件或配置，定位为"生成草稿 + 人工确认"。

---

## API 参考

完整 API 请查阅 Swagger：`http://localhost:8080/swagger-ui.html`

### 租户管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/tenants` | 所有租户列表 |
| `POST` | `/api/v1/tenants` | 创建租户 |
| `GET` | `/api/v1/tenants/{id}` | 租户详情 |
| `PUT` | `/api/v1/tenants/{id}` | 更新租户 |
| `DELETE` | `/api/v1/tenants/{id}` | 删除租户 |
| `POST` | `/api/v1/tenants/{id}/reinit` | 重新初始化引擎 |
| `PUT` | `/api/v1/tenants/{id}/content` | 保存 OWL/OBDA 内容 |
| `POST` | `/api/v1/tenants/{id}/apply` | 保存并应用 OWL/OBDA 内容 |

### 查询

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/tenants/{id}/sparql` | SPARQL 查询 |
| `POST` | `/api/v1/tenants/{id}/sparql/explain` | 查看 SPARQL → SQL 翻译 |
| `POST` | `/api/v1/tenants/{id}/sql` | SQL 执行 |
| `POST` | `/api/v1/tenants/{id}/nlq` | 自然语言查询 |
| `GET` | `/api/v1/tenants/{id}/nlq/stream` | 流式自然语言查询 |
| `POST` | `/graphql` | GraphQL 查询 |

### 规则引擎

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/tenants/{id}/rules` | 规则列表 |
| `POST` | `/api/v1/tenants/{id}/rules` | 创建规则 |
| `GET` | `/api/v1/tenants/{id}/rules/{rid}` | 规则详情 |
| `PUT` | `/api/v1/tenants/{id}/rules/{rid}` | 更新规则 |
| `DELETE` | `/api/v1/tenants/{id}/rules/{rid}` | 删除规则 |
| `POST` | `/api/v1/tenants/{id}/rules/{rid}/evaluate` | 评估规则 |
| `PATCH` | `/api/v1/tenants/{id}/rules/{rid}/toggle` | 开关规则 |
| `GET` | `/api/v1/tenants/{id}/rules/{rid}/history` | 评估历史 |

### 动作

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/tenants/{id}/actions` | 动作列表 |
| `POST` | `/api/v1/tenants/{id}/actions` | 创建动作 |
| `GET` | `/api/v1/tenants/{id}/actions/{aid}` | 动作详情 |
| `PUT` | `/api/v1/tenants/{id}/actions/{aid}` | 更新动作 |
| `DELETE` | `/api/v1/tenants/{id}/actions/{aid}` | 删除动作 |
| `POST` | `/api/v1/tenants/{id}/actions/{aid}/execute` | 执行动作（?dryRun=true） |

### 工作流

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/tenants/{id}/workflows` | 工作流列表 |
| `POST` | `/api/v1/tenants/{id}/workflows` | 创建工作流 |
| `GET` | `/api/v1/tenants/{id}/workflows/{wid}` | 工作流详情 |
| `PUT` | `/api/v1/tenants/{id}/workflows/{wid}` | 更新工作流 |
| `DELETE` | `/api/v1/tenants/{id}/workflows/{wid}` | 删除工作流 |
| `POST` | `/api/v1/tenants/{id}/workflows/{wid}/execute` | 执行工作流 |
| `POST` | `/api/v1/tenants/{id}/workflows/validate` | 验证 DAG |

### 文档

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/tenants/{id}/documents` | 文档列表 |
| `GET` | `/api/v1/tenants/{id}/documents/{did}` | 文档详情 |
| `POST` | `/api/v1/tenants/{id}/documents/upload` | 上传文档（multipart） |
| `DELETE` | `/api/v1/tenants/{id}/documents/{did}` | 删除文档 |
| `GET` | `/api/v1/tenants/{id}/documents/{did}/chunks` | 文档分块列表 |
| `POST` | `/api/v1/tenants/{id}/documents/query` | 语义搜索文档 |

### 本体辅助

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/tenants/{id}/ontology-assist/extract` | 从描述提取本体 |
| `GET` | `/api/v1/tenants/{id}/ontology-assist/ddl-hints` | 获取 DDL 元数据 |
| `POST` | `/api/v1/tenants/{id}/ontology-assist/generate-from-ddl` | 从 DDL 生成方案 |
| `GET` | `/api/v1/tenants/{id}/ontology-assist/proposals` | 提案列表 |
| `GET` | `/api/v1/tenants/{id}/ontology-assist/proposals/{pid}` | 提案详情 |
| `POST` | `/api/v1/tenants/{id}/ontology-assist/proposals/{pid}/apply` | 应用提案 |
| `POST` | `/api/v1/tenants/{id}/ontology-assist/proposals/{pid}/reject` | 拒绝提案 |
| `DELETE` | `/api/v1/tenants/{id}/ontology-assist/proposals/{pid}` | 删除提案 |

### 其他

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/health` | 健康检查 |
| `POST` | `/api/v1/auth/login` | 管理员登录获取 JWT |
| `GET` | `/api/v1/audit-log` | 审计日志 |
| `GET` | `/api/v1/api-keys` | API Key 管理 |
| `POST` | `/api/v1/saved-queries` | 保存查询 |
| `GET` | `/api/v1/tenants/{id}/query-history` | 查询历史 |
| `GET` | `/api/v1/tenants/{id}/mapping/owl` | 下载 OWL 文件 |
| `GET` | `/api/v1/tenants/{id}/mapping/obda` | 下载 OBDA 文件 |
| `GET` | `/api/v1/tenants/{id}/mapping/validate` | 验证 OBDA |
| `GET` | `/api/v1/tenants/{id}/graph` | 本体可视化数据 |
| `POST` | `/api/v1/tenants/{id}/generate-mapping` | 生成 OWL+OBDA 草稿 ZIP |
| `POST` | `/api/v1/tenants/{id}/mapping-assistant/draft` | Mapping Assistant 评审 |
| `POST` | `/api/v1/cache/evict` | 清空缓存 |
| `GET` | `/api/v1/rate-limit/status` | 速率限制状态 |

### SPARQL 查询格式

支持通过 Accept 头选择返回格式：

| 查询类型 | Accept 头 | 返回格式 |
|----------|-----------|----------|
| SELECT | `application/json` | 默认 JSON |
| SELECT | `application/sparql-results+json` | SPARQL JSON |
| SELECT | `application/sparql-results+xml` | SPARQL XML |
| SELECT | `text/csv` | CSV |
| SELECT | `text/tab-separated-values` | TSV |
| CONSTRUCT/DESCRIBE | `text/turtle` | Turtle |
| CONSTRUCT/DESCRIBE | `application/rdf+xml` | RDF/XML |
| CONSTRUCT/DESCRIBE | `application/ld+json` | JSON-LD |
| ASK | `application/json` | 默认 JSON |
| ASK | `application/sparql-results+json` | SPARQL JSON |

---

## 鉴权

### API Key

除健康检查、Swagger、静态页面外，其他接口默认需在请求头携带 API Key：

```bash
X-API-Key: admin-key-001
```

开发环境默认密钥：

| Key | 角色 | 用途 |
|-----|------|------|
| `admin-key-001` | ROLE_ADMIN | 管理接口、Mapping Assistant |
| `dev-key-002` | ROLE_DEV | 普通查询接口 |

### JWT 认证

管理员可通过登录接口获取 JWT：

```bash
curl -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  --data '{"username":"admin","password":"admin123"}'
```

在请求头中使用：

```bash
Authorization: Bearer <jwt-token>
```

### 生产安全

生产环境必须替换以下默认值：

- `ADMIN_PASSWORD` 环境变量
- `JWT_SECRET` 环境变量
- `ontology.auth.api-keys` 中的密钥
- `LLM_API_KEY` 环境变量（如使用 LLM 功能）

开启 `ontology.auth.strict-mode=true` 后，检测到默认密钥时应用拒绝启动。

---

## UI 页面

| 地址 | 功能 |
|------|------|
| `http://localhost:8080/` | 首页 |
| `http://localhost:8080/admin/` | 管理控制台（租户 CRUD、API Key、缓存、审计） |
| `http://localhost:8080/tenant/?id=sample` | 租户详情（SPARQL/SQL、OWL/OBDA 编辑、规则、动作、工作流、文档、本体辅助） |
| `http://localhost:8080/nlq/` | 自然语言查询 |
| `http://localhost:8080/nlq-examples/` | NLQ few-shot 示例 |
| `http://localhost:8080/saved-queries/` | 已保存查询 |
| `http://localhost:8080/query-history/` | 查询历史 |
| `http://localhost:8080/graphql-playground/` | GraphQL 控制台 |
| `http://localhost:8080/ontology-viz/` | 本体可视化 |
| `http://localhost:8080/mapping-assistant/` | Mapping Assistant 评审助手 |
| `http://localhost:8080/swagger-ui.html` | Swagger API 文档 |
| `http://localhost:8080/h2-console` | H2 数据库控制台 |

---

## 核心概念

| 概念 | 说明 |
|------|------|
| **租户 Tenant** | 一个可查询的数据源 = JDBC 连接 + OWL + OBDA |
| **OWL** | Web Ontology Language。定义业务概念（类）、属性（DatatypeProperty）、关系（ObjectProperty）、继承 |
| **OBDA** | Ontology-Based Data Access Mapping。定义 SQL 结果到 RDF 三元组的映射规则 |
| **SPARQL** | RDF 查询语言。类似 SQL，但查询的是语义模型而非表 |
| **Ontop** | SPARQL → SQL 查询改写引擎，项目核心依赖 |
| **SpEL** | Spring Expression Language。规则引擎使用 SpEL 评估条件表达式 |
| **MCP** | Model Context Protocol。AI Agent 与工具交互的标准协议 |
| **TF-IDF** | 词频-逆文档频率。文档导入使用 TF-IDF 进行语义向量化 |

---

## 开发验证

```bash
mvn test                    # 全部测试（H2 内存库）
mvn test -Dtest=ClassName   # 运行单个测试类
```

Java 类结构：

```
org.zhengyan.ontology.platform
├── config/        — Spring 配置（安全、缓存、Swagger、MCP...）
├── controller/    — REST API 控制器
├── engine/        — Ontop 引擎封装
├── exception/     — 全局异常处理
├── model/         — 数据模型 POJO
├── repository/    — JDBC 数据访问层
└── service/       — 业务逻辑层
```

### 项目文件

| 路径 | 说明 |
|------|------|
| `src/main/resources/application.yml` | 应用和租户配置 |
| `src/main/resources/db/init-*.sql` | 启动初始化 SQL |
| `src/main/resources/ontologies/` | 示例 OWL 和 OBDA 文件 |
| `src/main/resources/nlq-templates/` | NLQ 模板和 few-shot 示例 |
| `src/main/resources/static/` | 前端静态页面（零构建，纯 HTML/JS） |
| `pom.xml` | Maven 构建配置 |

---

## 能力边界

| 边界 | 说明 |
|------|------|
| 不是数据同步工具 | 查询时实时改写 SQL 访问原库，不复制数据 |
| 不是图数据库 | RDF 视图是虚拟的，性能取决于改写后的 SQL |
| 主要面向读取 | 支持 SELECT/CONSTRUCT/DESCRIBE/ASK，不含 SPARQL Update |
| 推理能力有限 | 支持 RDFS/OWL 基础推理（类继承、子属性），不等价完整 OWL DL 推理机 |
| 文档搜索为 TF-IDF | 内置 TF-IDF 余弦相似度，适合中小规模文档集。如需更高质量语义搜索，可接入外部向量数据库 |
| 规则引擎为 SpEL | 适合数千条规则规模。如需要前向链推理或规则量超 10K，可考虑集成 Drools |
| 自动生成是草稿 | 元数据生成的 OWL/OBDA 需要人工确认语义正确性 |
| 默认只带 H2 驱动 | 连接其他数据库需添加对应 JDBC Driver 依赖 |
| NLQ 不保证正确 | LLM 生成的 SPARQL 质量取决于模型和 Schema 提示 |
