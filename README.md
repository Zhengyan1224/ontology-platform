# Ontology Platform

Ontology Platform 是一个面向关系型数据库的语义查询服务。

它的核心作用是：不搬迁业务数据，也不强制把数据同步到图数据库，而是通过 **OWL 本体** 和 **OBDA 映射**，把已有的 MySQL、PostgreSQL、H2 等关系型数据库包装成一个可用 SPARQL、自然语言和 GraphQL 查询的语义访问层。

简单说：

```text
业务数据库表 + OWL 业务概念 + OBDA 映射规则
        |
        v
Ontology Platform
        |
        v
SPARQL / 自然语言 / GraphQL 查询接口
```

底层由 Ontop 把 SPARQL 查询实时改写成 SQL，再到原业务数据库执行。

## 这个项目是做什么的

这个项目解决的是“业务数据已经在关系型数据库里，但我希望按业务概念、关系和语义来查询”的问题。

例如业务库里可能是这些表：

```text
tb_books
tb_authors
tb_affiliated_writers
```

业务人员更关心的却是：

```text
Book
Author
Book writtenBy Author
```

本项目用 OWL 描述 `Book`、`Author` 这些业务概念，用 OBDA 描述数据库表字段如何映射到这些概念。之后用户可以查询：

```sparql
PREFIX : <http://meraka/moss/exampleBooks.owl#>

SELECT ?book ?title ?authorName WHERE {
  ?book a :Book ;
        :title ?title ;
        :writtenBy ?author .
  ?author :name ?authorName .
}
```

平台会把这段 SPARQL 翻译成 SQL，查询原来的关系型数据库，再把结果按 RDF/语义模型返回。

项目当前已经具备这些能力：

| 能力 | 说明 |
|------|------|
| 多租户数据源 | 一个租户对应一个业务数据库、一套 OWL、一套 OBDA |
| SPARQL 查询 | 支持 SELECT / CONSTRUCT / DESCRIBE / ASK 四种查询形式 |
| SPARQL 到 SQL 翻译 | 基于 Ontop 自动把 SPARQL 改写成 SQL |
| SPARQL 结果格式协商 | 支持 JSON、SPARQL JSON/XML、CSV、TSV、Turtle、RDF/XML、JSON-LD |
| 自然语言查询 | LLM 生成 SPARQL，失败时可回退到模板规则；支持 SSE 流式响应 |
| GraphQL 查询 | 提供 `/graphql` 查询入口 |
| 简单本体推理 | 支持类继承、子属性等 RDFS/OWL 层面的基础推理 |
| 本体图接口 | 可以把 OWL 中的类和属性转换成可视化图数据 |
| 查询缓存和审计 | Caffeine 缓存 SPARQL 结果，支持按租户驱逐；记录查询日志和 SPARQL 查询历史 |
| 查询历史 | 自动记录每个租户的 SPARQL 执行历史，支持浏览、重新执行和删除 |
| API Key / JWT | 提供双因子鉴权、租户访问控制（scope）和 Bucket4j 速率限制 |
| OWL/OBDA 草稿生成 | 可基于 JDBC 元数据生成初版 OWL/OBDA，并通过 LLM/规则助手输出解释、风险点和人工确认清单 |
| OBDA 映射校验 | 自动解析 OBDA 中的 SQL 并在目标数据库验证表和字段是否存在 |
| 联合查询 | 支持跨租户 SERVICE 查询，结果通过 VALUES 子句重写合并 |

## 核心概念

| 概念 | 在本项目中的含义 |
|------|------------------|
| 租户 tenant | 一个可查询的数据源。通常对应一个业务系统或一个数据库 |
| OWL | 业务概念模型。定义类、属性、继承关系，比如 `Product`、`Customer`、`Order` |
| OBDA | 表字段到 RDF 三元组的映射规则。告诉 Ontop 如何从 SQL 结果生成语义数据 |
| SPARQL | 面向语义模型的查询语言，类似 RDF 世界里的 SQL |
| Ontop | SPARQL 到 SQL 的查询改写引擎 |
| NLQ | Natural Language Query，自然语言查询。用户提问后生成 SPARQL 再执行 |

## 项目如何和业务系统对接

对接一个业务系统，本质上是新增一个租户。

一个租户需要四类信息：

```text
1. 业务数据库 JDBC 连接
2. OWL 本体文件
3. OBDA 映射文件
4. 租户配置
```

### 推荐接入流程

1. 梳理业务数据库中的核心实体。

   例如商品系统可以先识别 `Product`、`Category`、`Brand`、`Supplier`。

2. 设计 OWL 本体。

   OWL 里写业务概念，不是简单复制表名。表名可以叫 `t_prod_info`，但本体类建议叫 `Product`。

3. 编写 OBDA 映射。

   OBDA 里把 SQL 查询结果映射成 RDF 三元组。它负责连接“数据库表字段”和“OWL 业务概念”。

4. 在 `application.yml` 中增加租户配置。

5. 启动服务或调用 `/api/v1/tenants/{id}/reinit` 重新初始化租户。

6. 用 SPARQL、NLQ 或 GraphQL 查询。

### OWL 怎么写

OWL 负责描述业务概念和关系。

下面是一个商品系统的最小示例：

```xml
<?xml version="1.0"?>
<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
         xmlns:owl="http://www.w3.org/2002/07/owl#"
         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
         xmlns:xsd="http://www.w3.org/2001/XMLSchema#"
         xmlns="http://example.org/product#">

    <owl:Ontology rdf:about="http://example.org/product"/>

    <owl:Class rdf:about="http://example.org/product#Product"/>
    <owl:Class rdf:about="http://example.org/product#Category"/>

    <owl:DatatypeProperty rdf:about="http://example.org/product#name">
        <rdfs:domain rdf:resource="http://example.org/product#Product"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>
    </owl:DatatypeProperty>

    <owl:ObjectProperty rdf:about="http://example.org/product#belongsToCategory">
        <rdfs:domain rdf:resource="http://example.org/product#Product"/>
        <rdfs:range rdf:resource="http://example.org/product#Category"/>
    </owl:ObjectProperty>
</rdf:RDF>
```

常见建模建议：

| 数据库情况 | OWL 中建议建模 |
|------------|----------------|
| 主业务表 | 一个 `owl:Class` |
| 普通字段 | 一个 `owl:DatatypeProperty` |
| 外键关系 | 一个 `owl:ObjectProperty` |
| 类型表/字典表 | 可以建成类，也可以作为普通属性，取决于查询需求 |
| 表名/字段名很技术化 | 在 OWL 中改成业务友好的名字 |

### OBDA 怎么写

OBDA 负责把 SQL 查询结果变成 RDF 三元组。

假设业务库有两张表：

```sql
CREATE TABLE products (
    id INT PRIMARY KEY,
    name VARCHAR(200),
    category_id INT
);

CREATE TABLE categories (
    id INT PRIMARY KEY,
    name VARCHAR(200)
);
```

可以写成：

```text
[PrefixDeclaration]
:       <http://example.org/product#>
owl:    <http://www.w3.org/2002/07/owl#>
rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
rdfs:   <http://www.w3.org/2000/01/rdf-schema#>
xsd:    <http://www.w3.org/2001/XMLSchema#>

[MappingDeclaration] @collection [[
mappingId   cl_Product
target      :product/{id} a :Product ;
            :name "{name}" ;
            :belongsToCategory :category/{category_id} .
source      SELECT "id", "name", "category_id" FROM "products"

mappingId   cl_Category
target      :category/{id} a :Category ;
            :name "{name}" .
source      SELECT "id", "name" FROM "categories"
]]
```

关键点：

| 写法 | 含义 |
|------|------|
| `:product/{id}` | 用 `id` 列生成 Product 实例 IRI |
| `a :Product` | 声明这个实例是 Product |
| `:name "{name}"` | 把 `name` 列映射为字面量属性 |
| `:belongsToCategory :category/{category_id}` | 把外键映射为对象关系 |
| `source SELECT ...` | OBDA 所需数据来自哪条 SQL |

OBDA 中的 `source` 是发给业务数据库执行的 SQL。换数据库时，尽量使用标准 SQL；如果使用了某个数据库特有函数，迁移数据库时这部分仍然需要调整。

### 租户配置怎么写

租户配置在 `src/main/resources/application.yml` 的 `ontology.tenants` 下。

示例：

```yaml
ontology:
  tenants:
    - id: product
      name: Product System
      jdbc-url: jdbc:postgresql://localhost:5432/productdb
      jdbc-driver: org.postgresql.Driver
      jdbc-username: product_reader
      jdbc-password: change-me
      owl-path: ontologies/product.owl
      obda-path: ontologies/product.obda
```

路径说明：

| 配置项 | 说明 |
|--------|------|
| `jdbc-url` | 业务数据库 JDBC URL |
| `jdbc-driver` | JDBC Driver 类名。项目默认只带 H2 驱动，其他数据库需要在 `pom.xml` 增加驱动依赖 |
| `owl-path` | OWL 文件路径。相对路径会从 `src/main/resources` 下解析 |
| `obda-path` | OBDA 文件路径。相对路径会从 `src/main/resources` 下解析 |

也可以通过接口创建租户：

```http
POST /api/v1/tenants
Authorization: Bearer <admin-jwt>
Content-Type: application/json

{
  "id": "product",
  "name": "Product System",
  "jdbcUrl": "jdbc:postgresql://localhost:5432/productdb",
  "jdbcDriver": "org.postgresql.Driver",
  "jdbcUsername": "product_reader",
  "jdbcPassword": "change-me",
  "owlPath": "ontologies/product.owl",
  "obdaPath": "ontologies/product.obda"
}
```

创建接口会校验 JDBC 连接、OWL 文件和 OBDA 文件是否存在。

### 现有示例

项目内置两个租户，便于理解对接方式：

| 租户 | 文件 | 说明 |
|------|------|------|
| `sample` | `exampleBooks.owl`、`exampleBooks.obda` | 书籍、作者、版本的基础 OBDA 示例 |
| `university` | `university.owl`、`university.obda` | 大学人员、院系、继承和子属性推理示例 |

相关文件都在：

```text
src/main/resources/ontologies/
```

## 是否可以借助 LLM 自动生成 OWL 和 OBDA

可以，而且这是很适合接入业务系统的方向。

当前项目已经有一版“规则驱动”的自动生成能力：

```http
POST /api/v1/tenants/{tenantId}/generate-mapping
```

这个接口需要管理员权限。它会根据已存在租户的 JDBC 配置读取数据库元数据，并返回一个 ZIP 包，里面包含：

```text
{tenantId}.owl
{tenantId}.obda
```

当前生成规则大致是：

| 数据库元数据 | 自动生成结果 |
|--------------|--------------|
| 表 | OWL Class |
| 普通列 | OWL DatatypeProperty |
| 外键列 | OWL ObjectProperty |
| 主键 | OBDA IRI 模板 |
| 多对多中间表 | 可按配置生成对象关系，或跳过 |

相关配置在 `application.yml`：

```yaml
ontology:
  owl-generation:
    enabled: true
    output-dir: generated-ontologies
    name-case: PascalCase
    iri-template: "/{pk}"
    join-table-behavior: object-only
    mapping-style: per-table
```

需要注意：当前自动生成主要依赖 JDBC 元数据、表名、列名、主键和外键。它可以快速生成“可修改的草稿”，但还不能真正理解业务语义。

如果是一个全新的业务库，当前版本通常仍需要先准备最小 OWL/OBDA 文件并创建租户，之后再调用 `generate-mapping` 生成更完整的草稿。更理想的后续形态，是新增一个“输入 JDBC 连接信息，直接生成 OWL/OBDA 草稿”的接入向导接口。

项目也提供了一个 LLM/规则草稿评审助手。它的定位不是“自动改生产映射”，而是“读取数据库元数据后，生成可 review 的 OWL/OBDA 草稿，并解释风险和下一步需要人工确认的事项”。

页面地址：

```text
http://localhost:8080/mapping-assistant/index.html
```

接口地址：

```http
POST /api/v1/tenants/{tenantId}/mapping-assistant/draft
```

权限要求：

```text
需要管理员权限。开发环境可以使用 API Key：admin-key-001
```

请求示例：

```json
{
  "businessContext": "商品中心，products 是商品主表，categories 是类目表。",
  "focus": "security",
  "includeDraftFiles": true,
  "useLlm": true,
  "maxPromptChars": 14000
}
```

字段说明：

| 字段 | 说明 |
|------|------|
| `businessContext` | 可选。补充业务背景，帮助 LLM 更准确理解表和字段 |
| `focus` | 可选。评审重点，例如 `general`、`naming`、`security`、`quality` |
| `includeDraftFiles` | 是否在响应里返回完整 `owlDraft` 和 `obdaDraft` |
| `useLlm` | 是否尝试调用 LLM。没有配置有效 `LLM_API_KEY` 时会自动回退到规则模式 |
| `maxPromptChars` | 发送给 LLM 的最大上下文长度，避免元数据过大 |

响应里重点看这些字段：

| 字段 | 说明 |
|------|------|
| `mode` | `llm` 或 `rules`，表示本次评审来自 LLM 还是规则回退 |
| `llmAvailable` | 当前是否真的调用了 LLM |
| `draftOnly` | 固定为 `true`，表示只生成草稿 |
| `applied` | 固定为 `false`，表示不会自动应用到项目配置或生产映射 |
| `metadataSummary` | 从 JDBC 读取到的表、字段、主键、外键摘要 |
| `reviewMarkdown` | 面向人的评审说明、命名建议和风险提示 |
| `warnings` | 规则识别出的风险，例如敏感字段、缺少主键、缺少外键 |
| `nextSteps` | 建议人工继续确认或修改的事项 |
| `owlDraft` | 生成的 OWL 草稿，`includeDraftFiles=true` 时返回 |
| `obdaDraft` | 生成的 OBDA 草稿，`includeDraftFiles=true` 时返回 |

推荐使用流程：

1. 启动服务后打开 `http://localhost:8080/mapping-assistant/index.html`。
2. 在页面里输入管理员 API Key，例如开发环境的 `admin-key-001`。
3. 选择要分析的租户。
4. 填写业务背景和评审重点。
5. 生成草稿，先看 `Warnings`、`Next steps` 和 `Review`。
6. 人工确认类名、属性名、敏感字段、权限边界和 OBDA SQL 后，再决定是否把草稿保存成正式 `.owl` / `.obda` 文件。

它有几个刻意设计的边界：

```text
不会写入 .owl / .obda 文件
不会修改 application.yml
不会更新已保存的租户配置
不会绕过人工 review 直接上线
```

如果配置了有效的 OpenAI 兼容接口，助手会调用 LLM 生成更容易阅读的评审说明；如果 `LLM_API_KEY` 为空或仍是 `sk-placeholder`，它会自动使用规则模式，仍然可以生成 OWL/OBDA 草稿和基础风险提示。

后续接入 LLM 后，可以做得更像一个业务接入向导：

1. 自动读取数据库表、字段、主外键、索引、注释。
2. 可选读取少量样例数据，帮助判断字段含义。
3. 用 LLM 把技术表名转换为业务概念名，例如 `t_cust_info` -> `Customer`。
4. 自动补充 `rdfs:label`、`rdfs:comment`、同义词和自然语言查询模板。
5. 自动生成 OWL、OBDA、示例 SPARQL 和 NLQ few-shot 示例。
6. 运行校验查询，检查生成的 OBDA 是否能被 Ontop 初始化并返回数据。
7. 输出差异和风险点，由人确认后再上线。

建议把 LLM 定位成“生成和解释草稿的助手”，不要直接让它无审核地修改生产映射。原因是表名、字段名、外键并不总能表达真实业务语义，权限和敏感字段也需要人工确认。

## 怎么启动

### 环境要求

| 依赖 | 要求 |
|------|------|
| Java | 21 |
| Maven | 使用项目 `pom.xml` 构建 |
| 网络 | 首次构建需要下载 Maven 依赖，Ontop 依赖来自 `https://maven.ontop.informatik.uni-bremen.de/releases` |

### 启动命令

```bash
mvn spring-boot:run
```

也可以使用：

```bash
mvn exec:java
```

注意：项目里的 `spring-boot-maven-plugin` 配置了 `skip=true`，所以开发时推荐使用 `spring-boot:run` 或 `exec:java`。

### 打包成 JAR 并启动

当前 `pom.xml` 中 Spring Boot repackage 默认是跳过的：

```xml
<skip>true</skip>
```

因此直接执行 `mvn package` 只会生成普通 JAR，不是可直接 `java -jar` 启动的 Spring Boot 可执行 JAR。

如果只是临时打一个可执行 JAR，可以运行：

```bash
mvn clean package spring-boot:repackage -DskipTests -Dspring-boot.repackage.skip=false
```

打包完成后，JAR 文件位于：

```text
target/ontology-platform-1.0.0-SNAPSHOT.jar
```

启动服务：

```bash
java -jar target/ontology-platform-1.0.0-SNAPSHOT.jar
```

Windows PowerShell 示例：

```powershell
$env:ADMIN_PASSWORD="change-me"
$env:JWT_SECRET="change-me-change-me-change-me-change-me"
java -jar target\ontology-platform-1.0.0-SNAPSHOT.jar
```

Linux/macOS 示例：

```bash
ADMIN_PASSWORD=change-me \
JWT_SECRET=change-me-change-me-change-me-change-me \
java -jar target/ontology-platform-1.0.0-SNAPSHOT.jar
```

也可以指定端口：

```bash
java -jar target/ontology-platform-1.0.0-SNAPSHOT.jar --server.port=8081
```

如果希望以后直接通过 `mvn clean package` 生成可执行 JAR，可以把 `pom.xml` 里的 `<skip>true</skip>` 改成 `<skip>false</skip>`，或直接删除这一行。

注意：默认 H2 文件库路径是 `./data/ontology-platform`，它是相对于启动命令所在目录解析的。生产部署时建议在固定目录启动，或把 `spring.datasource.url` 和租户 JDBC URL 改成绝对路径。

### 默认地址

| 地址 | 说明 |
|------|------|
| `http://localhost:8080/` | 平台首页，所有功能入口 |
| `http://localhost:8080/admin/` | 管理控制台：租户 CRUD、API Key、缓存、审计日志 |
| `http://localhost:8080/tenant/?id=sample` | SPARQL 查询编辑器，支持 SELECT / CONSTRUCT / DESCRIBE / ASK |
| `http://localhost:8080/saved-queries/` | 已保存 SPARQL 查询，支持保存、分享、删除 |
| `http://localhost:8080/query-history/` | SPARQL 查询历史浏览，支持按租户查看、重新执行和删除 |
| `http://localhost:8080/nlq/` | 自然语言查询多轮对话页面 |
| `http://localhost:8080/nlq-examples/` | 每个租户的 few-shot 示例查看 |
| `http://localhost:8080/graphql-playground/` | GraphQL 交互式查询控制台 |
| `http://localhost:8080/ontology-viz/index.html` | 本体可视化：类和属性关系图 |
| `http://localhost:8080/mapping-assistant/index.html` | OWL/OBDA 草稿生成与 LLM/规则评审 |
| `http://localhost:8080/api/v1/health` | 健康检查 |
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/h2-console` | H2 Console |

默认平台库使用 H2 文件模式：

```text
./data/ontology-platform
```

也就是说租户、API Key、审计日志、JWT 黑名单等平台数据会在重启后保留。测试环境仍使用 H2 内存库。

H2 Console 默认连接信息：

```text
JDBC URL: jdbc:h2:file:./data/ontology-platform;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
User: sa
Password: 留空
```

### 鉴权说明

除健康检查、登录、Swagger、H2 Console 和静态可视化页面外，其它接口默认需要鉴权。

开发配置会 seed 两个 API Key：

| API Key | 角色 | 用途 |
|---------|------|------|
| `admin-key-001` | `ROLE_ADMIN` | 管理接口 |
| `dev-key-002` | `ROLE_DEV` | 普通查询接口 |

本体可视化和 Mapping Assistant 的静态页面可以直接打开；页面调用后端 API 时仍需要填写 API Key。Mapping Assistant 会读取数据库元数据并生成映射草稿，因此必须使用管理员权限，`dev-key-002` 不够。

示例 SPARQL 查询：

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/tenants/sample/sparql" `
  -H "X-API-Key: dev-key-002" `
  -H "Content-Type: application/sparql-query" `
  --data "PREFIX : <http://meraka/moss/exampleBooks.owl#> SELECT ?book ?title WHERE { ?book a :Book ; :title ?title . } LIMIT 5"
```

示例自然语言查询：

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/tenants/sample/nlq" `
  -H "X-API-Key: dev-key-002" `
  -H "Content-Type: application/json" `
  --data "{\"question\":\"List all authors\"}"
```

也可以使用管理员账号换取 JWT：

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/auth/login" `
  -H "Content-Type: application/json" `
  --data "{\"username\":\"admin\",\"password\":\"admin123\"}"
```

生产环境必须替换默认密钥：

```text
ADMIN_PASSWORD
JWT_SECRET
LLM_API_KEY
ontology.auth.api-keys
```

如果开启 `ontology.auth.strict-mode=true`，检测到默认密钥时应用会拒绝启动。

## 常用 API

完整 API 以 Swagger 为准：`http://localhost:8080/swagger-ui.html`。

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/health` | 健康检查 |
| `GET` | `/api/v1/tenants` | 查看所有租户 |
| `POST` | `/api/v1/tenants` | 创建租户，需要管理员权限 |
| `PUT` | `/api/v1/tenants/{id}` | 更新租户，需要管理员权限 |
| `DELETE` | `/api/v1/tenants/{id}` | 删除租户，需要管理员权限 |
| `GET` | `/api/v1/tenants/{id}/schema` | 查看租户的本体和映射摘要 |
| `POST` | `/api/v1/tenants/{id}/reinit` | 重新初始化租户引擎，需要管理员权限 |
| `POST` | `/api/v1/tenants/{id}/sparql` | 执行 SPARQL 查询（SELECT / CONSTRUCT / DESCRIBE / ASK） |
| `POST` | `/api/v1/tenants/{id}/sparql/explain` | 查看 SPARQL 被翻译成什么 SQL |
| `GET` | `/api/v1/tenants/{id}/mapping/owl` | 下载 OWL Turtle 文件，需要管理员权限 |
| `GET` | `/api/v1/tenants/{id}/mapping/obda` | 下载 OBDA 映射文件，需要管理员权限 |
| `GET` | `/api/v1/tenants/{id}/mapping/validate` | 校验 OBDA 映射中的 SQL 表和字段，需要管理员权限 |
| `GET` | `/api/v1/tenants/{id}/query-history` | 查询该租户的 SPARQL 执行历史，需要管理员权限 |
| `POST` | `/api/v1/tenants/{id}/nlq` | 自然语言查询 |
| `GET` | `/api/v1/tenants/{id}/nlq/stream` | 自然语言查询 SSE 流式响应 |
| `GET` | `/api/v1/tenants/{id}/nlq/examples` | 查看租户的 NLQ few-shot 示例 |
| `GET` | `/api/v1/tenants/{id}/graph` | 获取本体可视化图数据 |
| `POST` | `/api/v1/tenants/{id}/generate-mapping` | 基于数据库元数据生成 OWL + OBDA 的 ZIP 包，需要管理员权限 |
| `POST` | `/api/v1/tenants/{id}/generate-owl` | （已废弃）仅生成 OWL，需要管理员权限 |
| `POST` | `/api/v1/tenants/{id}/mapping-assistant/draft` | 生成 OWL/OBDA 草稿并返回 LLM/规则评审，需要管理员权限 |
| `GET` | `/api/v1/audit-log` | 查询审计日志（分页、按租户过滤），需要管理员权限 |
| `POST` | `/api/v1/audit-log/clear` | 清空审计日志，需要管理员权限 |
| `GET` | `/api/v1/api-keys` | 查看 API Key，需要管理员权限 |
| `POST` | `/api/v1/api-keys` | 创建 API Key，需要管理员权限 |
| `POST` | `/api/v1/saved-queries` | 保存 SPARQL 查询（附分享 token） |
| `GET` | `/api/v1/saved-queries/{shareToken}` | 通过分享 token 查看已保存查询 |
| `GET` | `/api/v1/tenants/{id}/saved-queries` | 按租户列出已保存查询（分页） |
| `DELETE` | `/api/v1/saved-queries/{id}` | 删除已保存查询 |
| `DELETE` | `/api/v1/query-history/{id}` | 删除指定 SPARQL 查询历史记录，需要管理员权限 |
| `POST` | `/api/v1/cache/evict` | 清空所有缓存，需要管理员权限 |
| `GET` | `/api/v1/rate-limit/status` | 查看当前速率限制状态 |
| `POST` | `/graphql` | GraphQL 查询入口 |

### SPARQL 返回格式

`POST /api/v1/tenants/{id}/sparql` 可以通过 `Accept` 请求头选择返回格式。

SELECT 查询支持的格式：

| `Accept` | 返回格式 |
|----------|----------|
| `application/json` | 默认 JSON（含 queryType、variables、results） |
| `application/sparql-results+json` | SPARQL JSON |
| `application/sparql-results+xml` | SPARQL XML |
| `text/csv` | CSV |
| `text/tab-separated-values` | TSV |

CONSTRUCT / DESCRIBE 查询支持的格式：

| `Accept` | 返回格式 |
|----------|----------|
| `text/turtle` | Turtle |
| `application/rdf+xml` | RDF/XML |
| `application/ld+json` | JSON-LD |
| `application/json` | 默认 JSON（含 queryType、graphModel） |

ASK 查询支持的格式：

| `Accept` | 返回格式 |
|----------|----------|
| `application/json` | 默认 JSON（含 booleanQueryResult、queryType） |
| `application/sparql-results+json` | SPARQL JSON |

如果 `Accept` 与查询类型不匹配（例如 CONSTRUCT 请求 `text/csv`），返回 `406 Not Acceptable`。

## 能力边界

这个项目适合做语义查询层，但不是所有数据集成问题的完整替代品。

| 边界 | 说明 |
|------|------|
| 不是数据同步工具 | 它默认不复制业务数据，而是在查询时通过 Ontop 改写 SQL 访问原库 |
| 不是完整图数据库 | RDF 视图是虚拟的，查询性能取决于 SPARQL 改写后的 SQL 和业务库索引 |
| 主要面向读取查询 | 当前支持 `SELECT`、`CONSTRUCT`、`DESCRIBE`、`ASK` 四种查询形式；SPARQL Update 不在当前范围 |
| 推理能力有限 | 适合类继承、子属性等轻量语义推理，不等价于完整 OWL DL 推理机 |
| 自动生成只是草稿 | 元数据生成不能完全理解业务语义，Mapping Assistant 也只返回草稿和评审建议，不会自动写入生产 OWL/OBDA |
| 换数据库仍需验证 | OBDA 的 `source` SQL 会发给目标数据库执行。标准 SQL 可迁移性更好，数据库特有函数仍要调整 |
| 默认只带 H2 驱动 | PostgreSQL、MySQL、SQL Server 等需要在 `pom.xml` 增加对应 JDBC Driver |
| NLQ 不保证总是正确 | LLM 生成的 SPARQL 需要通过 schema、模板、示例、审计和 explain 逐步约束 |
| 生产安全要加固 | 默认 API Key、默认管理员密码、默认 JWT Secret 只适合开发环境 |

## 常用文件

| 文件或目录 | 说明 |
|------------|------|
| `src/main/resources/application.yml` | 应用配置、租户配置、H2 文件库配置 |
| `src/main/resources/ontologies/` | 示例 OWL 和 OBDA 文件 |
| `src/main/resources/db/` | 启动初始化 SQL |
| `src/main/resources/nlq-templates/` | 自然语言查询模板、prompt 和 few-shot 示例 |
| `src/main/resources/static/` | 全部前端静态页面（零构建） |
| `src/main/resources/static/admin/` | 管理控制台页面 |
| `src/main/resources/static/tenant/` | SPARQL 查询编辑器页面 |
| `src/main/resources/static/saved-queries/` | 已保存查询页面 |
| `src/main/resources/static/query-history/` | 查询历史页面 |
| `src/main/resources/static/nlq/` | 自然语言查询页面 |
| `src/main/resources/static/nlq-examples/` | NLQ few-shot 示例页面 |
| `src/main/resources/static/graphql-playground/` | GraphQL 交互式控制台页面 |
| `src/main/resources/static/ontology-viz/` | 本体可视化页面 |
| `src/main/resources/static/mapping-assistant/` | OWL/OBDA 草稿生成和 LLM/规则评审页面 |
| `docs/roadmap.md` | 开发路线图 |
| `docs/future-directions.md` | 后续演进方向 |

## 开发验证

运行测试：

```bash
mvn test
```

测试配置使用 H2 内存库，不会污染本地 `./data/ontology-platform` 文件库。
