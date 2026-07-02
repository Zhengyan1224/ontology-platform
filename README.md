# Ontology Service Platform

本体数据访问（OBDA）服务平台，基于 Ontop 5.x + RDF4J 实现虚拟 RDF 视图，支持 SPARQL 查询、自然语言查询（LLM + 模板）以及本体推理。

---

## 目录

- [快速开始](#快速开始)
- [架构概览](#架构概览)
- [本体与映射编写指南](#本体与映射编写指南)
  - [1. OWL 本体设计](#1-owl-本体设计)
  - [2. OBDA 映射文件](#2-obda-映射文件)
  - [3. R2RML 替代方案](#3-r2rml-替代方案)
  - [4. 完整示例：Book 租户](#4-完整示例book-租户)
  - [5. 完整示例：University 租户（含推理）](#5-完整示例university-租户含推理)
- [添加新租户](#添加新租户)
- [API 文档](#api-文档)
- [Swagger UI](#swagger-ui)
- [配置文件](#配置文件)

---

## 快速开始

```bash
# 启动（默认端口 8080）
mvn spring-boot:run

# 访问 Swagger UI
open http://localhost:8080/swagger-ui.html
```

预置两个租户：
| 租户 | 描述 | 示例查询 |
|------|------|----------|
| `sample` | 书籍作者数据库 | `List all authors` |
| `university` | 大学人员推理演示 | `List all employees` |

---

## 架构概览

```
┌──────────────┐    SPARQL / NLQ    ┌──────────────────────┐
│   Client     │ ──────────────────>│   REST Controllers   │
└──────────────┘                    └──────────────────────┘
                                            │
                                    ┌───────┴───────┐
                                    │  Ontop Engine  │  ← OBDA 映射 + OWL 本体
                                    └───────┬───────┘
                                            │ SQL 重写
                                    ┌───────┴───────┐
                                    │  Relational DB │  ← H2 / PostgreSQL / MySQL
                                    └───────────────┘
```

工作流程：
1. 用户提交 SPARQL 查询
2. **Ontop** 根据 OWL 本体 + OBDA 映射将 SPARQL **重写为 SQL**
3. SQL 在关系数据库上执行
4. 结果映射回 RDF 三元组返回

---

## 本体与映射编写指南

### 1. OWL 本体设计

OWL 本体定义领域的概念模型。文件位于 `src/main/resources/ontologies/`，使用 RDF/XML 格式。

#### 基本结构

```xml
<?xml version="1.0"?>
<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
         xmlns:owl="http://www.w3.org/2002/07/owl#"
         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
         xmlns:xsd="http://www.w3.org/2001/XMLSchema#"
         xmlns="http://example.org/myontology#">

    <owl:Ontology rdf:about="http://example.org/myontology"/>
    ...
</rdf:RDF>
```

> **重要**：`xmlns="http://example.org/myontology#"` 定义了默认前缀 `:`，所有类名和属性名会附加在此 IRI 之后。

#### 声明类（Classes）

```xml
<!-- 简单类 -->
<owl:Class rdf:about="http://example.org/myontology#Person"/>

<!-- 等价写法（利用默认前缀） -->
<owl:Class rdf:about="http://example.org/myontology#Person"/>
```

#### 声明类层次（Subclass / 推理支持）

```xml
<!-- Professor ⊑ Employee ⊑ Person -->
<rdfs:Class rdf:about="http://example.org/university#Professor">
    <rdfs:subClassOf rdf:resource="http://example.org/university#Employee"/>
</rdfs:Class>

<rdfs:Class rdf:about="http://example.org/university#Employee">
    <rdfs:subClassOf rdf:resource="http://example.org/university#Person"/>
</rdfs:Class>
```

效果：
- `:Professor` 的实例自动也是 `:Employee` 和 `:Person`
- 查询 `?p a :Person` 会返回所有教授
- 查询 `?p a :Employee` 会返回教授 + 普通员工

#### 声明数据属性（Datatype Properties）

```xml
<owl:DatatypeProperty rdf:about="http://example.org/myontology#name">
    <rdfs:domain rdf:resource="http://example.org/myontology#Person"/>
    <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>
</owl:DatatypeProperty>
```

`rdfs:domain` 和 `rdfs:range` 是可选的，但建议填写以支持推理。

#### 声明对象属性（Object Properties）

```xml
<!-- 表示 Person 与 Department 之间的关系 -->
<owl:ObjectProperty rdf:about="http://example.org/university#worksFor">
    <rdfs:domain rdf:resource="http://example.org/university#Person"/>
    <rdfs:range rdf:resource="http://example.org/university#Department"/>
</owl:ObjectProperty>

<!-- 子属性：headOf ⊑ worksFor -->
<owl:ObjectProperty rdf:about="http://example.org/university#headOf">
    <rdfs:subPropertyOf rdf:resource="http://example.org/university#worksFor"/>
    <rdfs:domain rdf:resource="http://example.org/university#Employee"/>
    <rdfs:range rdf:resource="http://example.org/university#Department"/>
</owl:ObjectProperty>
```

效果：
- `:headOf` 的实例自动推理为 `:worksFor`
- 查询 `?p :worksFor ?dept` 同时返回 `:headOf` 的结果

---

### 2. OBDA 映射文件

OBDA 文件（`.obda`）将关系数据库的表/列映射到 RDF 三元组。文件格式为 Ontop 原生格式。

#### 文件结构

```
[PrefixDeclaration]
:       <http://example.org/myontology#>
owl:    <http://www.w3.org/2002/07/owl#>
rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
xsd:    <http://www.w3.org/2001/XMLSchema#>
rdfs:   <http://www.w3.org/2000/01/rdf-schema#>

[MappingDeclaration] @collection [[
mappingId   <唯一ID>
target      <RDF 三元组模板>
source      <SQL SELECT 查询>
]]
```

#### 语法详解

**`mappingId`**：映射唯一标识，建议使用有意义的名称。

**`target`**：RDF 三元组模板。
- `{column_name}` 引用 SQL 查询返回的列值
- 类型字面量：`"{value}"^^xsd:string`、`"{value}"^^xsd:integer`
- 未加引号的 `{value}` 生成 IRI
- 字符串用 `"{value}"` 生成字面量

**`source`**：标准 SQL SELECT 查询。
- 列名需要与 `target` 中的 `{column_name}` 对应
- 支持 JOIN、WHERE、聚合等

#### 映射模式分类

| 模式 | target 示例 | 说明 |
|------|------------|------|
| 类实例映射 | `:emp-{emp_code} rdf:type :Employee .` | 将一行映射为类的实例 |
| 数据属性映射 | `:emp-{emp_code} :name "{emp_name}"^^xsd:string .` | 将列值映射为属性值 |
| 对象属性映射 | `:emp-{emp_code} :worksFor :dept-{dept_code} .` | 将外键映射为对象关系 |
| 混合映射 | `:emp-{emp_code} rdf:type :Employee ; :name "{emp_name}" .` | 一行生成多个三元组 |

#### IRI 生成规则

| 模板 | 生成的 IRI | 说明 |
|------|-----------|------|
| `:emp-{emp_code}` | `http://.../university#emp-E001` | 前缀 + `-` + 列值 |
| `:book/{bk_code}` | `http://.../exampleBooks.owl#book/1` | 前缀 + `/` + 列值 |
| `:author/{wr_name}` | `http://.../exampleBooks.owl#author/J.K.%20Rowling` | 可包含空格 |

---

### 3. R2RML 替代方案

除 OBDA 原生格式外，Ontop 也支持 W3C 标准的 **R2RML**（RDB to RDF Mapping Language）。

R2RML 是一个 RDF 图，描述了从关系数据库到 RDF 数据集的映射。

#### 示例（与 OBDA 等价）

```ttl
@prefix rr: <http://www.w3.org/ns/r2rml#> .
@prefix : <http://example.org/university#> .

:TriplesMapEmployee
    rr:logicalTable [ rr:tableName "tb_employees" ];
    rr:subjectMap [
        rr:template "http://example.org/university#emp-{emp_code}";
        rr:class :Employee
    ];
    rr:predicateObjectMap [
        rr:predicate :name;
        rr:objectMap [ rr:column "emp_name" ]
    ].
```

| 特性 | OBDA 原生格式 | R2RML |
|------|--------------|-------|
| 语法简洁性 | ★★★★★（一行一个映射） | ★★★（较啰嗦） |
| W3C 标准 | 否（Ontop 专有） | 是 |
| 表达能力 | 相同 | 相同 |
| 推荐场景 | 快速开发 | 需要标准化的场景 |

使用方式：在 `application.yml` 中将 `.obda` 文件改为 `.ttl` 文件即可：

```yaml
ontology:
  tenants:
    - id: mytenant
      obda-path: mytenant.ttl   # 支持 .obda 和 .ttl 格式
```

---

### 4. 完整示例：Book 租户

#### 数据库表

```sql
CREATE TABLE tb_authors (
    bk_code INT,        -- 书籍代码
    wr_id   INT,        -- 作者 ID
    PRIMARY KEY (bk_code, wr_id)
);
CREATE TABLE tb_books (
    bk_code  INT PRIMARY KEY,
    bk_title VARCHAR(255)
);
CREATE TABLE tb_edition (
    ed_code  INT PRIMARY KEY,
    pub_date TIMESTAMP,
    n_edt    INT,
    bk_id    INT
);
CREATE TABLE tb_affiliated_writers (
    wr_code INT PRIMARY KEY,
    wr_name VARCHAR(255)
);
```

#### OWL 本体（`exampleBooks.owl`）

定义了 `:Author`、`:Book`、`:Edition` 三个类，以及 `:name`、`:title`、`:writtenBy`、`:hasEdition` 等属性。

```xml
<owl:Class rdf:about="http://meraka/moss/exampleBooks.owl#Author"/>
<owl:Class rdf:about="http://meraka/moss/exampleBooks.owl#Book"/>
<owl:ObjectProperty rdf:about="http://meraka/moss/exampleBooks.owl#writtenBy">
    <rdfs:domain rdf:resource="http://meraka/moss/exampleBooks.owl#Book"/>
    <rdfs:range rdf:resource="http://meraka/moss/exampleBooks.owl#Author"/>
</owl:ObjectProperty>
```

#### OBDA 映射（`exampleBooks.obda`）

```
[PrefixDeclaration]
:    http://meraka/moss/exampleBooks.owl#

[MappingDeclaration] @collection [[
mappingId  cl_Authors
target     :author/{wr_code} a :Author ; :name {wr_name} .
source     SELECT wr_code, wr_name FROM tb_affiliated_writers

mappingId  cl_Books
target     :book/{bk_code} a :Book ; :title {bk_title} .
source     SELECT bk_code, bk_title FROM tb_books

mappingId  op_writtenBy
target     :book/{bk_code} :writtenBy :author/{wr_id} .
source     SELECT bk_code, wr_id FROM tb_authors
]]
```

#### 生成的 RDF 示例

```turtle
:book/1 a :Book ; :title "Harry Potter and the Philosophers Stone" ;
        :writtenBy :author/1 .
:author/1 a :Author ; :name "J.K. Rowling" .
```

#### SPARQL 查询示例

```sparql
PREFIX : <http://meraka/moss/exampleBooks.owl#>
SELECT ?author ?name WHERE {
    ?book a :Book .
    ?book :title ?title .
    ?book :writtenBy ?author .
    ?author :name ?name .
    FILTER(CONTAINS(LCASE(?title), "harry potter"))
}
```

---

### 5. 完整示例：University 租户（含推理）

#### 数据库表

```sql
CREATE TABLE tb_departments (
    dept_code VARCHAR(20) PRIMARY KEY,
    dept_name VARCHAR(100)
);
CREATE TABLE tb_employees (
    emp_code VARCHAR(20) PRIMARY KEY,
    emp_name VARCHAR(100)
);
CREATE TABLE tb_professors (
    prof_code  VARCHAR(20) PRIMARY KEY,
    prof_name  VARCHAR(100),
    dept_code  VARCHAR(20) REFERENCES tb_departments(dept_code)
);
CREATE TABLE tb_dept_heads (
    emp_code  VARCHAR(20) PRIMARY KEY REFERENCES tb_employees(emp_code),
    dept_code VARCHAR(20) REFERENCES tb_departments(dept_code)
);
```

#### OWL 本体（`university.owl`）—— 层次推理

```
Person
  └── Employee
        └── Professor
```

**Object Properties：**
- `worksFor` : Person → Department
- `headOf` ⊑ `worksFor` : Employee → Department（子属性推理）

**效果验证：**

| SPARQL 查询 | 结果 |
|------------|------|
| `?p a :Person` | 所有员工 + 所有教授 + 系主任 |
| `?p a :Employee` | 普通员工 + 所有教授 |
| `?p a :Professor` | 仅教授 |
| `?p :worksFor :dept-CS` | 在 CS 系工作的所有人（含系主任） |
| `?p :headOf ?dept` | 仅系主任 |

#### OBDA 映射（`university.obda`）

```
mappingId  departments
target     :dept-{dept_code} a :Department ; :departmentName "{dept_name}" .
source     SELECT dept_code, dept_name FROM tb_departments

mappingId  employees
target     :emp-{emp_code} a :Employee ; :name "{emp_name}" .
source     SELECT emp_code, emp_name FROM tb_employees

mappingId  professors
target     :prof-{prof_code} a :Professor ; :name "{prof_name}" ;
               :worksFor :dept-{dept_code} .
source     SELECT prof_code, prof_name, dept_code FROM tb_professors

mappingId  deptHeads
target     :emp-{emp_code} :headOf :dept-{dept_code} .
source     SELECT emp_code, dept_code FROM tb_dept_heads
```

#### 推理在 SPARQL 中的体现

```
PREFIX : <http://example.org/university#>

-- 查询所有雇员（含教授）：subclass 推理
SELECT ?person ?name WHERE { ?person a :Employee . ?person :name ?name . }
-- 返回：Alice Admin, Bob Secretary, Carol Professor, Dave Professor, Eve Professor

-- 查询在 CS 系工作的人：subProperty 推理
SELECT ?person ?name WHERE {
    ?person :worksFor ?dept .
    ?dept :departmentName "Computer Science" .
    ?person :name ?name .
}
-- 返回：Carol Professor（教授映射），Alice Admin（系主任映射 via headOf ⊑ worksFor）
```

---

## 添加新租户

三步即可添加一个新数据源：

### 步骤 1：编写 OWL 本体

`src/main/resources/ontologies/mydata.owl`

```xml
<owl:Class rdf:about="http://example.org/mydata#Product"/>
<owl:Class rdf:about="http://example.org/mydata#Category"/>
```

### 步骤 2：编写 OBDA 映射

`src/main/resources/ontologies/mydata.obda`

```
[PrefixDeclaration]
:    <http://example.org/mydata#>

[MappingDeclaration] @collection [[
mappingId  cl_Products
target     :product/{id} a :Product ; :name "{name}" .
source     SELECT id, name FROM products
]]
```

### 步骤 3：添加配置

`application.yml`：

```yaml
ontology:
  tenants:
    - id: mydata
      name: My Data Source
      jdbc-url: jdbc:postgresql://localhost:5432/mydb
      jdbc-driver: org.postgresql.Driver
      jdbc-username: user
      jdbc-password: pass
      owl-path: ontologies/mydata.owl
      obda-path: ontologies/mydata.obda
```

重启服务后：
```
GET  /api/v1/tenants            → 新租户出现在列表
POST /api/v1/tenants/mydata/sparql  → 可以查询
POST /api/v1/tenants/mydata/nlq     → 自然语言查询
```

---

## API 文档

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/health` | 健康检查 |
| GET | `/api/v1/tenants` | 列出所有租户 |
| GET | `/api/v1/tenants/{id}/schema` | 查看租户本体描述 |
| POST | `/api/v1/tenants/{id}/reinit` | 重新初始化引擎 |
| POST | `/api/v1/tenants/{id}/sparql` | 执行 SPARQL 查询（支持多种输出格式，通过 `Accept` 头控制） |
| POST | `/api/v1/tenants/{id}/sparql/explain` | SPARQL → SQL 翻译 |
| POST | `/api/v1/tenants/{id}/nlq` | 自然语言查询（可选 `sessionId` 字段支持多轮对话） |
| GET | `/api/v1/tenants/{id}/nlq/stream?question=...&sessionId=...` | 自然语言查询 SSE 流式响应（分阶段推送 status → sparql → result → complete） |
| GET | `/api/v1/audit-log` | 查看审计日志 |
| POST | `/api/v1/audit-log/clear` | 清空审计日志 |

### NLQ 自然语言查询

自然语言查询支持两级 SPARQL 生成策略：**LLM**（需配置真实 API key）→ **模板回退**。

#### 模板驱动（`nlq-templates/{tenantId}.yml`）

每个租户可以通过 YAML 文件自定义自然语言 → SPARQL 映射规则，无需修改 Java 代码：

```yaml
rules:
  - patterns:
      - "list.*(all\\s+)?employees?"
      - "who works here"
    sparql: |
      PREFIX : <http://example.org/university#>
      SELECT ?person ?name WHERE { ?person a :Employee . ?person :name ?name . }
    description: "List all employees"
  - patterns:
      - "who\\s+works?\\s+for\\s+(.+?)(\\?)?$"
    sparql: "SELECT ?person ?name WHERE { ?person :worksFor ?dept . ?dept :departmentName \"{1}\" . ?person :name ?name . }"
    params:
      - group: 1
```

- 规则按顺序匹配，返回第一个匹配的 SPARQL
- `{1}`, `{2}` 引用正则捕获组的值
- 无 YAML 文件时自动回退到 Java 硬编码模板

#### LLM Prompt 优化

Prompt 模板外部化为 `nlq-templates/prompt-template.txt`，支持占位符：
- `{{tenantId}}`、`{{schema}}`、`{{question}}`、`{{examples}}`、`{{history}}`

每个租户可配置 few-shot 示例（`{tenantId}-examples.yml`），schema 描述带缩进类层次结构。提取 SPARQL 后自动校验是否包含 `SELECT` 或 `CONSTRUCT`。

#### 流式响应（SSE）

`GET /api/v1/tenants/{id}/nlq/stream` 使用 Server-Sent Events 分阶段推送：

```
event: status
data: {"stage":"translating"}

event: sparql
data: {"sparql":"SELECT ?person ?name WHERE {...}"}

event: result
data: {"variables":["person","name"],"results":[...],"executionTimeMs":1234}

event: complete
data: {}
```

#### 多轮对话

在请求中传入 `sessionId` 即可启用会话上下文：

```json
POST /api/v1/tenants/sample/nlq
{"question": "list all authors", "sessionId": "my-session"}
```

后续请求携带同一 `sessionId`，历史 Q/A 将注入 LLM prompt。会话默认 30 分钟无活动自动过期。

### SPARQL 输出格式

`POST /api/v1/tenants/{id}/sparql` 通过 `Accept` 请求头选择返回格式：

| `Accept` 值 | 格式 | 说明 |
|-------------|------|------|
| `application/json`（默认） | JSON | 标准 JSON 对象（Spring 序列化） |
| `application/sparql-results+json` | SPARQL JSON | 遵循 SPARQL 1.1 Query Results JSON 格式 |
| `application/sparql-results+xml` | SPARQL XML | 遵循 SPARQL 1.1 Query Results XML 格式 |
| `text/csv` | CSV | 逗号分隔值 |
| `text/tab-separated-values` | TSV | 制表符分隔值 |

请求体支持 `Content-Type: application/json`（`{"query": "..."}`）和 `Content-Type: application/sparql-query`（裸 SPARQL 字符串，仅返回 JSON 格式）。

完整 API 文档请访问 Swagger UI：`http://localhost:8080/swagger-ui.html`

---

## Swagger UI

Swagger（SpringDoc OpenAPI）默认开启。可通过配置关闭：

```yaml
ontology:
  swagger:
    enabled: false     # 关闭 Swagger UI
```

访问地址：`http://localhost:8080/swagger-ui.html`

---

## 配置文件

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `ontology.swagger.enabled` | `true` | 是否启用 Swagger |
| `ontology.nlq.template-path` | `nlq-templates` | NLQ YAML 模板和 prompt 文件目录 |
| `ontology.nlq.llm.api-key` | `sk-placeholder` | LLM API 密钥（设为占位符时使用模板模式） |
| `ontology.nlq.llm.model` | `gpt-4o-mini` | LLM 模型名称 |
| `ontology.nlq.llm.base-url` | (空) | LLM API 基础 URL（可用于兼容 OpenAI 的代理） |
| `ontology.nlq.stream.timeout` | `60000` | SSE 流式响应超时时间（毫秒） |
| `ontology.nlq.session.ttl` | `1800000` | 会话过期时间（毫秒，默认 30 分钟） |
| `ontology.nlq.session.max` | `1000` | 最大同时会话数 |
| `ontology.nlq.session.cleanup-interval` | `300000` | 过期会话清理间隔（毫秒，默认 5 分钟） |
| `ontology.tenants` | — | 租户列表 |
