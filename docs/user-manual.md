# Ontology Platform 操作使用手册

## 目录

1. [快速开始](#1-快速开始)
2. [前端页面导览](#2-前端页面导览)
3. [SPARQL 查询详解](#3-sparql-查询详解)
4. [自然语言查询](#4-自然语言查询)
5. [管理控制台](#5-管理控制台)
6. [OBDA 映射校验](#6-obda-映射校验)
7. [查询历史与已保存查询](#7-查询历史与已保存查询)
8. [租户管理](#8-租户管理)
9. [鉴权与安全](#9-鉴权与安全)
10. [常见问题](#10-常见问题)

---

## 1. 快速开始

### 1.1 启动服务

```bash
mvn spring-boot:run
```

服务启动后默认地址：`http://localhost:8080`

### 1.2 验证服务是否正常运行

```powershell
curl.exe http://localhost:8080/api/v1/health
```

返回 `{"status":"UP"}` 表示服务正常。

### 1.3 第一次 SPARQL 查询

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/tenants/sample/sparql" `
  -H "X-API-Key: dev-key-002" `
  -H "Content-Type: application/json" `
  -d '{\"query\":\"SELECT ?s ?p ?o WHERE {?s ?p ?o} LIMIT 5\"}'
```

内置租户 `sample` 使用 H2 内存库，包含书籍、作者等示例数据。

### 1.4 第一次自然语言查询

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/tenants/sample/nlq" `
  -H "X-API-Key: dev-key-002" `
  -H "Content-Type: application/json" `
  -d '{\"question\":\"List all books\"}'
```

### 1.5 打开首页

浏览器访问 `http://localhost:8080/` 查看所有功能入口卡片。

---

## 2. 前端页面导览

所有前端页面为纯静态 HTML，无构建步骤，直接通过浏览器访问。

### 2.1 首页 `/`

所有功能的入口。按功能分为"任意 API Key"和"需 Admin API Key"两类。

### 2.2 SPARQL 查询编辑器 `/tenant/?id={tenantId}`

- 支持 SELECT / CONSTRUCT / DESCRIBE / ASK 四种查询
- 通过 Accept 下拉框选择返回格式（JSON / Turtle / RDF/XML 等）
- 查询结果自动识别类型并渲染（表格 / 三元组计数 / true/false 徽章）
- 右上角显示执行耗时
- 需填入 API Key 才能执行

### 2.3 管理控制台 `/admin/`

需要管理员 API Key。包含：
- 租户列表与 CRUD
- API Key 管理与 scope 配置
- 缓存清理
- 审计日志查看和清空
- 租户引擎状态查看与重新初始化

### 2.4 自然语言查询 `/nlq/`

- 基于 SSE 流式响应的多轮对话页面
- 支持 LLM 模式和模板规则模式自动回退
- 通过 `X-Session-Id` 维持会话上下文

### 2.5 NLQ 示例 `/nlq-examples/`

查看每个租户的 few-shot 示例（question + SPARQL 对），了解 NLQ 模板匹配模式。

### 2.6 已保存查询 `/saved-queries/`

- 保存 SPARQL 查询（含可选的 question 和 result summary）
- 生成分享链接，可分享给他人
- 按租户浏览和删除

### 2.7 查询历史 `/query-history/`

- 自动记录每个租户的历史 SPARQL 查询
- 支持展开查看完整 SPARQL
- 支持重新执行（跳转到 SPARQL 编辑器并填充查询）
- 支持单条删除
- 需要管理员 API Key

### 2.8 GraphQL Playground `/graphql-playground/`

基于 GraphiQL 的交互式控制台，支持 SPARQL、NLQ 等 GraphQL 接口。

### 2.9 本体可视化 `/ontology-viz/index.html`

以节点图形式展示 OWL 本体中的类、属性和关系，支持搜索、过滤和交互拖拽。

### 2.10 Mapping Assistant `/mapping-assistant/index.html`

- 读取 JDBC 数据库元数据
- 生成 OWL/OBDA 草稿（可配置业务上下文、评审重点）
- 使用 LLM 或规则模式生成评审说明和风险提示
- 不自动写入生产文件，仅输出草稿供人工确认

---

## 3. SPARQL 查询详解

### 3.1 查询类型

| 类型 | 说明 | 返回格式 |
|------|------|----------|
| `SELECT` | 返回表格结果 | JSON / SPARQL-JSON / SPARQL-XML / CSV / TSV |
| `CONSTRUCT` | 返回 RDF 图 | Turtle / RDF/XML / JSON-LD |
| `DESCRIBE` | 返回资源的 RDF 描述 | Turtle / RDF/XML / JSON-LD |
| `ASK` | 返回 true/false | JSON / SPARQL-JSON |

### 3.2 请求方式

**方式一：JSON 请求体（推荐）**

```http
POST /api/v1/tenants/{tenantId}/sparql
Content-Type: application/json
Accept: application/json
X-API-Key: dev-key-002

{"query": "SELECT ?book ?title WHERE { ?book a :Book ; :title ?title } LIMIT 5"}
```

**方式二：原生 SPARQL 请求体**

```http
POST /api/v1/tenants/{tenantId}/sparql
Content-Type: application/sparql-query
X-API-Key: dev-key-002

SELECT ?book ?title WHERE { ?book a :Book ; :title ?title } LIMIT 5
```

### 3.3 格式协商

通过 `Accept` 请求头控制返回格式。

SELECT 查询：
- `application/json` → 默认 JSON（含 queryType、variables、results）
- `application/sparql-results+json` → SPARQL JSON
- `application/sparql-results+xml` → SPARQL XML
- `text/csv` → CSV
- `text/tab-separated-values` → TSV

CONSTRUCT / DESCRIBE 查询：
- `text/turtle` → Turtle
- `application/rdf+xml` → RDF/XML
- `application/ld+json` → JSON-LD
- `application/json` → 默认 JSON（含 queryType、graphModel）

ASK 查询：
- `application/json` → 默认 JSON（含 booleanQueryResult、queryType）
- `application/sparql-results+json` → SPARQL JSON

不匹配时返回 `406 Not Acceptable`。

### 3.4 查询示例

```sparql
# SELECT — 查询所有书籍和标题
SELECT ?book ?title WHERE {
  ?book a :Book ; :title ?title
} LIMIT 10

# CONSTRUCT — 返回 RDF 图
CONSTRUCT { ?book a :Book ; :title ?title }
WHERE { ?book a :Book ; :title ?title }

# DESCRIBE — 描述某个资源
PREFIX : <http://meraka/moss/exampleBooks.owl#>
DESCRIBE :book/1

# ASK — 是否存在匹配的数据
ASK { ?book a :Book }
```

### 3.5 查看翻译后的 SQL

```http
POST /api/v1/tenants/{tenantId}/sparql/explain
Content-Type: application/json
X-API-Key: dev-key-002

{"query": "SELECT ?book ?title WHERE { ?book a :Book ; :title ?title }"}
```

返回 SPARQL 对应的 SQL，用于调试和性能分析。

---

## 4. 自然语言查询

### 4.1 基本使用

```http
POST /api/v1/tenants/{tenantId}/nlq
Content-Type: application/json
X-API-Key: dev-key-002

{"question": "List all authors and their books"}
```

### 4.2 SSE 流式响应

```http
GET /api/v1/tenants/{tenantId}/nlq/stream?question=List all authors
X-API-Key: dev-key-002
```

通过 Server-Sent Events 逐段返回 SPARQL 生成过程和结果。

### 4.3 工作模式

平台支持两种 NLQ 模式：

| 模式 | 条件 | 行为 |
|------|------|------|
| **LLM 模式** | 配置了有效的 OpenAI 兼容 API Key | 调用 LLM 生成 SPARQL |
| **模板模式** | API Key 为 `sk-placeholder` 或 LLM 不可用 | 根据 YAML 模板规则匹配 |

### 4.4 few-shot 示例管理

每个租户可配置 few-shot 示例，帮助 LLM 了解表结构和查询模式：

```yaml
# src/main/resources/nlq-templates/sample-examples.yml
examples:
  - question: "List all books"
    sparql: "SELECT ?book ?title WHERE { ?book a :Book ; :title ?title }"
  - question: "Find authors who wrote a book"
    sparql: "SELECT ?author ?name WHERE { ?book :writtenBy ?author . ?author :name ?name }"
```

查看示例页面：`http://localhost:8080/nlq-examples/`

---

## 5. 管理控制台

### 5.1 访问

打开 `http://localhost:8080/admin/`，输入管理员 API Key（开发环境：`admin-key-001`）。

### 5.2 功能

| 功能 | 说明 |
|------|------|
| 租户列表 | 查看所有注册租户的基础信息和引擎状态 |
| 创建租户 | 输入 JDBC 连接、OWL 路径、OBDA 路径添加新租户 |
| 编辑租户 | 修改租户的 JDBC、OWL、OBDA 配置 |
| 删除租户 | 删除租户配置 |
| 重新初始化 | 修改 OWL 或 OBDA 文件后重新加载引擎 |
| API Key 管理 | 创建/删除 API Key，设置 tenant scope |
| 缓存清理 | 清空 SPARQL 结果缓存 |
| 审计日志 | 查看和清空查询审计日志 |

---

## 6. OBDA 映射校验

### 6.1 校验端点

```http
GET /api/v1/tenants/{tenantId}/mapping/validate
X-API-Key: admin-key-001
```

### 6.2 校验内容

| 校验项 | 说明 |
|--------|------|
| 映射 ID 提取 | 解析 OBDA 中每个 mappingId |
| 目标 IRI 列引用 | 检查 `{col_name}` 中的列在 source SQL 中是否存在 |
| 源表存在性 | 验证 source SELECT 中的表在目标数据库中是否存在 |
| 源列存在性 | 验证 SELECT 中的列在目标数据库表中是否存在 |

### 6.3 返回结果

```json
{
  "valid": true,
  "errors": [],
  "warnings": ["Table 'old_books' not found in database"]
}
```

### 6.4 在 ZIP 生成时自动校验

调用 `POST /api/v1/tenants/{tenantId}/generate-mapping` 时，校验结果通过响应头返回：

- `X-Validation-Valid`: true/false
- `X-Validation-Errors`: 错误列表（逗号分隔）
- `X-Validation-Warnings`: 警告列表（逗号分隔）

---

## 7. 查询历史与已保存查询

### 7.1 查询历史

每次 SPARQL 执行成功后自动记录到查询历史。记录内容：

| 字段 | 说明 |
|------|------|
| tenantId | 所属租户 |
| apiKeyId | 执行者的 API Key ID |
| sparql | 查询文本（超 10KB 截断） |
| executionTimeMs | 执行耗时 |
| createdAt | 记录时间 |

查询历史默认保留 30 天，可通过 `ontology.query-history.retention-days` 配置。

**查看历史：**
- 页面：`http://localhost:8080/query-history/`
- API：`GET /api/v1/tenants/{tenantId}/query-history?limit=50&offset=0`

**删除历史：**
- API：`DELETE /api/v1/query-history/{id}`
- 每日凌晨 3 点自动清理过期记录

### 7.2 已保存查询

**保存：**
```http
POST /api/v1/saved-queries
Content-Type: application/json

{
  "tenantId": "sample",
  "question": "List all books",
  "sparql": "SELECT ?book ?title WHERE { ?book a :Book ; :title ?title }",
  "resultSummary": "50 results"
}
```

**分享：** 保存后返回 `shareToken` 和 `shareUrl`，可通过分享 URL 让其他人查看。

**浏览（分页）：**
```http
GET /api/v1/tenants/sample/saved-queries?limit=20&offset=0
```

---

## 8. 租户管理

### 8.1 配置文件方式

编辑 `src/main/resources/application.yml`：

```yaml
ontology:
  tenants:
    - id: myapp
      name: My Application
      jdbc-url: jdbc:postgresql://localhost:5432/mydb
      jdbc-driver: org.postgresql.Driver
      jdbc-username: reader
      jdbc-password: secret
      owl-path: ontologies/myapp.owl
      obda-path: ontologies/myapp.obda
```

### 8.2 API 方式

```http
POST /api/v1/tenants
Authorization: Bearer <admin-jwt>
Content-Type: application/json

{
  "id": "myapp",
  "name": "My Application",
  "jdbcUrl": "jdbc:postgresql://localhost:5432/mydb",
  "jdbcDriver": "org.postgresql.Driver",
  "jdbcUsername": "reader",
  "jdbcPassword": "secret",
  "owlPath": "ontologies/myapp.owl",
  "obdaPath": "ontologies/myapp.obda"
}
```

### 8.3 自动生成 OWL/OBDA

已有租户配置后，可通过 JDBC 元数据自动生成 OWL 和 OBDA 草稿：

```http
POST /api/v1/tenants/{tenantId}/generate-mapping
X-API-Key: admin-key-001
```

返回 ZIP 包含 `{tenantId}.owl` 和 `{tenantId}.obda` 文件。

生成规则：

| 数据库元数据 | 生成结果 |
|--------------|----------|
| 表 | OWL Class + OBDA Class Mapping |
| 普通列 | OWL DatatypeProperty + OBDA 列映射 |
| 外键 | OWL ObjectProperty + OBDA 对象属性映射 |
| 主键 | OBDA IRI 模板 |
| 多对多中间表 | 根据配置生成对象关系或跳过 |

---

## 9. 鉴权与安全

### 9.1 认证方式

平台支持两种认证方式：

| 方式 | 适用场景 | 传递方式 |
|------|----------|----------|
| API Key | 程序调用、开发测试 | `X-API-Key` 请求头 |
| JWT Token | 管理界面、需要角色控制 | `Authorization: Bearer <token>` |

### 9.2 API Key

开发环境预置了两个 API Key：

| API Key | 角色 | 权限 |
|---------|------|------|
| `admin-key-001` | ROLE_ADMIN | 全部接口，包括管理操作 |
| `dev-key-002` | ROLE_DEV | 仅查询接口（SPARQL、NLQ、GraphQL） |

API Key 可设置 `tenant_scopes` 限制可访问的租户。
多个 scope 用逗号分隔，例如 `sample,university` 表示只能访问这两个租户。

### 9.3 JWT 登录

```http
POST /api/v1/auth/login
Content-Type: application/json

{"username": "admin", "password": "admin123"}
```

返回的 JWT 用于后续请求的 `Authorization: Bearer <token>` 头。

### 9.4 速率限制

默认配置（可通过 `application.yml` 调整）：

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `ontology.rate-limit.capacity` | 20 | 令牌桶容量 |
| `ontology.rate-limit.refund-per-minute` | 10 | 每分钟补充的令牌数 |

### 9.5 生产安全建议

| 事项 | 建议 |
|------|------|
| 默认密码 | 替换 `ADMIN_PASSWORD`、`JWT_SECRET` 环境变量 |
| 默认 API Key | 修改 `application.yml` 中的 `ontology.auth.api-keys` |
| 数据库密码 | 租户 JDBC 密码使用强密码 |
| 严格模式 | 设置 `ontology.auth.strict-mode: true`，检测默认密钥时拒绝启动 |
| HTTPS | 生产部署建议前置反向代理启用 HTTPS |

---

## 10. 常见问题

### 10.1 查询返回空结果

可能原因：
- OBDA 映射的 source SQL 未匹配到数据
- OWL/OBDA 文件修改后未重新初始化租户 → 调用 `POST /api/v1/tenants/{id}/reinit`

### 10.2 添加新数据库驱动

项目默认只带 H2 驱动。其他数据库需在 `pom.xml` 增加依赖：

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.4</version>
</dependency>
```

### 10.3 修改 OWL/OBDA 后不生效

修改文件后需要重新初始化引擎：

```http
POST /api/v1/tenants/{tenantId}/reinit
X-API-Key: admin-key-001
```

或者在管理控制台点击"重新初始化"按钮。

### 10.4 SPARQL 查询慢

- 检查翻译后的 SQL 是否使用了合适的索引
- 考虑是否可以通过 OBDA 中的 source SQL 优化
- 确认是否命中缓存（同一查询第二次更快）
- 添加 `LIMIT` 减少返回数据量

### 10.5 ASK 查询返回 406

ASK 查询只支持 JSON 和 SPARQL-JSON 格式。如果 `Accept` 请求头设置为 `text/csv` 或 `text/turtle` 等不兼容格式，会返回 406。

### 10.6 如何备份平台数据

平台数据包括：
- H2 文件库：`./data/ontology-platform`（SQL 兼容模式，可用 `H2 Console` 导出）
- OWL/OBDA 文件：`src/main/resources/ontologies/`
- 配置文件：`application.yml`

建议在变更前备份以上所有文件。

### 10.7 忘记管理员密码

开发环境默认管理员密码为 `admin123`。如果生产环境忘记：

1. 通过 `ADMIN_PASSWORD` 环境变量可以覆盖配置
2. 直接修改 `application.yml` 中 `ontology.auth.admin-password` 的值并重启
