## ADDED Requirements

### Requirement: Accept Header 内容协商
`POST /api/v1/tenants/{tenantId}/sparql` 端点 SHALL 根据请求的 `Accept` header 返回对应格式：
- `Accept: application/json`（默认） — 返回现有 JSON 格式
- `Accept: application/sparql-results+xml` — SPARQL Results XML
- `Accept: text/csv` — CSV
- `Accept: text/tab-separated-values` — TSV
- `Accept: application/sparql-results+json` — SPARQL Results JSON 标准格式
未匹配的 `Accept` header SHALL 回退到 `application/json`。

#### Scenario: 默认 JSON 格式
- **WHEN** 请求 SPARQL 端点不指定 `Accept` header
- **THEN** 返回 `Content-Type: application/json` 的 JSON 结果

#### Scenario: SPARQL XML 格式
- **WHEN** 请求指定 `Accept: application/sparql-results+xml`
- **THEN** 返回 `Content-Type: application/sparql-results+xml` 的 XML 结果

#### Scenario: CSV 格式
- **WHEN** 请求指定 `Accept: text/csv`
- **THEN** 返回 `Content-Type: text/csv` 的 CSV 结果

### Requirement: CONSTRUCT 查询支持
系统 SHALL 支持 SPARQL CONSTRUCT 查询，返回 RDF 图数据。
当查询类型为 CONSTRUCT 时，系统 SHALL 使用 RDF4J Rio 将结果序列化为：
- `Accept: text/turtle` — Turtle 格式
- `Accept: application/rdf+xml` — RDF/XML
- `Accept: application/ld+json` — JSON-LD
- 默认回退到 Turtle

#### Scenario: CONSTRUCT 查询返回 Turtle
- **WHEN** 请求 `POST /api/v1/tenants/{tenantId}/sparql` 包含 CONSTRUCT 查询且 `Accept: text/turtle`
- **THEN** 返回 `Content-Type: text/turtle` 的 Turtle 数据

#### Scenario: CONSTRUCT 查询返回 JSON-LD
- **WHEN** 请求包含 CONSTRUCT 查询且 `Accept: application/ld+json`
- **THEN** 返回 `Content-Type: application/ld+json` 的 JSON-LD 数据

### Requirement: 格式错误处理
当请求的格式无法被序列化时，系统 SHALL 返回 406 Not Acceptable。

#### Scenario: 不支持格式返回 406
- **WHEN** 请求指定不支持的 `Accept` 类型（如 `Accept: application/xml`）
- **THEN** 返回 406 Not Acceptable
