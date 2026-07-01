# Ontology Service Platform

基于 **Spring Boot 3 + Ontop 5** 的通用本体服务中间件，提供 **OBDA（Ontology-Based Data Access）** 能力，让业务系统只需提供本体（`.owl`）+ 映射（`.obda`）+ 数据库连接即可获得 SPARQL 查询、OWL 2 QL 推理和自然语言问答能力。

---

## 特性

- **OBDA 虚拟视图** — 无需 ETL，SPARQL 查询实时翻译为 SQL 执行
- **OWL 2 QL 推理** — 子类继承（`Professor ⊑ Employee`）、子属性（`headOf ⊑ worksFor`）自动展开为 `UNION ALL` SQL
- **NLQ 自然语言问答** — 支持 LLM（OpenAI 兼容）和模板双模式，对用户透明
- **多租户隔离** — 每个租户独立的本体 + 映射 + 数据库连接，运行时热加载
- **管理员 API** — 租户注册/删除/重载、审计日志查询
- **Prometheus 指标** — QPS、延迟分位数、引擎数等开箱即用

---

## 架构

```
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  SPARQL API  │   │   NLQ API    │   │ Admin API    │
└──────┬───────┘   └──────┬───────┘   └──────┬───────┘
       │                  │                  │
       ▼                  ▼                  ▼
┌──────────────────────────────────────────────────────┐
│                  Service Layer                        │
│  SparqlTemplateGenerator / NaturalLanguageQueryService│
│  AuditService / MetricsService                        │
└──────────┬───────────────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────────────────────┐
│  EngineRegistry (多租户引擎管理)                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐           │
│  │ Ontop    │  │ Ontop    │  │ Ontop    │  ← 热加载  │
│  │ Engine 1 │  │ Engine 2 │  │ Engine N │           │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘           │
└───────┼──────────────┼──────────────┼───────────────┘
        │              │              │
        ▼              ▼              ▼
     DB 1           DB 2           DB N    ← 每个租户独立
```

---

## 技术栈

| 组件 | 技术选型 |
|------|---------|
| 语言 | Java 21 |
| 框架 | Spring Boot 3.4.3 |
| OBDA 引擎 | Ontop 5.5.0 |
| 查询语言 | SPARQL 1.1 / RDF4J 5.1.4 |
| LLM 集成 | LangChain4j 1.0.0-beta3 |
| 监控 | Micrometer + Prometheus |
| 数据库 | H2（开发/测试），可切换 MySQL/PostgreSQL |
| 构建 | Maven |
| 测试 | JUnit 5 |

---

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+

### 构建

```bash
cd ontology-platform
mvn clean package -DskipTests
```

### 运行

```bash
java -jar target/ontology-platform-1.0.0-SNAPSHOT.jar
```

启动后访问 http://localhost:8080

### 运行测试

```bash
mvn test
```

全部 21+ 个测试通过。

---

## 配置说明

### 多租户配置（`application.yml`）

```yaml
ontology:
  tenants:
    - id: sample                       # 租户唯一标识
      name: Sample Books Database      # 租户名称
      jdbc-url: jdbc:h2:mem:testdb     # JDBC 连接 URL
      jdbc-driver: org.h2.Driver       # JDBC 驱动
      jdbc-username: sa                # 用户名
      jdbc-password:                   # 密码
      owl-path: ontologies/exampleBooks.owl   # 本体文件路径
      obda-path: ontologies/exampleBooks.obda # 映射文件路径

    - id: university
      name: University Reasoning Demo
      jdbc-url: jdbc:h2:mem:testdb
      jdbc-driver: org.h2.Driver
      jdbc-username: sa
      jdbc-password:
      owl-path: ontologies/university.owl
      obda-path: ontologies/university.obda
```

### NLQ LLM 配置（可选）

```yaml
ontology:
  nlq:
    llm:
      api-key: sk-xxx            # OpenAI 兼容 API Key
      model: gpt-4o-mini         # 模型名
      base-url:                  # 自定义 API 地址（如 vllm/ollama）
```

不配置 `api-key` 时自动降级为模板模式。

### 监控端点

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,info,metrics
```

---

## API 参考

### 健康检查

```
GET /api/v1/health
GET /api/v1/tenants/{id}/health
```

### 租户信息

```
GET /api/v1/tenants
GET /api/v1/tenants/{id}/info
```

### SPARQL 查询

```
POST /api/v1/tenants/{id}/sparql
Content-Type: application/json

{"query": "SELECT ?author ?name WHERE { ?author a :Author . ?author :name ?name . }"}
```

```
POST /api/v1/tenants/{id}/sparql
Content-Type: application/sparql-query

SELECT ?author ?name WHERE { ?author a :Author . ?author :name ?name . }
```

### SPARQL 翻译（Explain）

```
POST /api/v1/tenants/{id}/sparql/explain

{"query": "SELECT ?person ?name WHERE { ?person a :Employee . ?person :name ?name . }"}
```

### 自然语言问答

```
POST /api/v1/tenants/{id}/nlq

{"question": "list all employees"}
```

### 管理员 API

```
POST   /api/v1/admin/tenants                  # 注册新租户
DELETE /api/v1/admin/tenants/{id}             # 删除租户
POST   /api/v1/admin/tenants/{id}/reload      # 重载租户
GET    /api/v1/admin/audit                    # 审计日志
GET    /api/v1/admin/status                   # 系统状态
```

---

## 使用示例

### 图书租户（sample）

```bash
# 列出所有作者
curl -s http://localhost:8080/api/v1/tenants/sample/sparql \
  -H 'Content-Type: application/json' \
  -d '{"query": "PREFIX : <http://meraka/moss/exampleBooks.owl#> SELECT ?author ?name WHERE { ?author a :Author . ?author :name ?name }"}' | jq

# 自然语言
curl -s http://localhost:8080/api/v1/tenants/sample/nlq \
  -H 'Content-Type: application/json' \
  -d '{"question": "who wrote Harry Potter"}' | jq
```

### 大学租户（university）— 推理演示

```bash
# 查询 Employee（自动展开 Professor ⊑ Employee）
curl -s http://localhost:8080/api/v1/tenants/university/sparql \
  -H 'Content-Type: application/json' \
  -d '{"query": "PREFIX : <http://example.org/university#> SELECT ?person ?name WHERE { ?person a :Employee . ?person :name ?name }"}' | jq

# 查看翻译后的 SQL
curl -s http://localhost:8080/api/v1/tenants/university/sparql/explain \
  -H 'Content-Type: application/json' \
  -d '{"query": "PREFIX : <http://example.org/university#> SELECT ?person ?name WHERE { ?person a :Employee . ?person :name ?name }"}' | jq
```

输出示例（Employee 查询的 SQL 翻译）：

```sql
SELECT ... FROM TB_EMPLOYEES
UNION ALL
SELECT ... FROM TB_PROFESSORS
```

### 指标监控

```bash
# Prometheus 格式
curl http://localhost:8080/actuator/prometheus

# 健康检查
curl http://localhost:8080/actuator/health
```

---

## 项目结构

```
ontology-platform/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/ontology/platform/
    │   │   ├── OntologyPlatformApplication.java    # 应用入口
    │   │   ├── config/
    │   │   │   ├── TenantConfig.java               # YAML 配置绑定
    │   │   │   └── OntologyInitializer.java        # 启动初始化
    │   │   ├── controller/
    │   │   │   ├── HealthController.java           # 健康 + 信息 API
    │   │   │   ├── SparqlController.java           # SPARQL 查询 API
    │   │   │   ├── NlqController.java              # NLQ 自然语言 API
    │   │   │   └── AdminController.java            # 管理员 API
    │   │   ├── engine/
    │   │   │   ├── OntologyEngine.java             # 引擎接口
    │   │   │   ├── OntopEngine.java                # Ontop 实现
    │   │   │   └── EngineRegistry.java             # 多租户注册管理
    │   │   ├── exception/
    │   │   │   ├── OntologyPlatformException.java  # 自定义异常
    │   │   │   └── GlobalExceptionHandler.java     # 全局异常处理
    │   │   ├── model/
    │   │   │   ├── Tenant.java                     # 租户配置 POJO
    │   │   │   ├── SparqlQueryRequest.java         # SPARQL 请求体
    │   │   │   └── SparqlQueryResult.java          # 查询结果
    │   │   └── service/
    │   │       ├── AuditService.java               # 审计日志
    │   │       ├── MetricsService.java             # 自定义指标
    │   │       ├── NaturalLanguageQueryService.java # NLQ 引擎
    │   │       ├── NlqRequest.java                 # NLQ 请求
    │   │       ├── NlqResult.java                  # NLQ 响应
    │   │       ├── OntologySchemaProvider.java     # 本体描述
    │   │       ├── QueryAuditLog.java              # 审计日志模型
    │   │       └── SparqlTemplateGenerator.java    # SPARQL 模板
    │   └── resources/
    │       ├── application.yml
    │       ├── ontologies/
    │       │   ├── exampleBooks.owl                # 图书本体
    │       │   ├── exampleBooks.obda               # 图书映射
    │       │   ├── university.owl                  # 大学本体（含推理）
    │       │   └── university.obda                 # 大学映射
    │       └── db/
    │           ├── init-books.sql
    │           └── init-university.sql
    └── test/
        ├── java/com/ontology/platform/
        │   ├── OntologyPlatformApplicationTests.java # 基础测试
        │   ├── ReasoningTests.java                  # 推理测试
        │   ├── NlqTests.java                        # NLQ 测试
        │   └── AdminTests.java                      # 管理测试
        └── resources/
            └── application.yml
```

---

## 本体与映射文件

### 图书本体（sample）

本体示例（`exampleBooks.owl`）包含 `:Author`、`:Book` 和 `:AffiliatedWriter` 类，以及 `:name`、`:title`、`:writtenBy` 属性。映射文件（`exampleBooks.obda`）将其连接到 `tb_authors`、`tb_books` 等关系表。

### 大学本体（university）— 推理演示

本体（`university.owl`）演示 OWL 2 QL 推理：
- **子类**：`Professor ⊑ Employee ⊑ Person`
- **子属性**：`headOf ⊑ worksFor`

对应的 SQL 映射是分表存储的：`tb_employees`（一般员工）和 `tb_professors`（教授）。查询 `:Employee` 时，Ontop 自动生成 `UNION ALL` 跨越两张表。

---

## 从关系数据库转为 OBDA

将任意关系数据库接入本平台的步骤：

1. **编写本体（OWL）** — 定义业务概念的类与属性
2. **编写映射（OBDA/R2RML）** — 定义 SQL 查询到 RDF 三元组的映射关系
3. **配置租户** — 在 `application.yml` 中添加数据库连接
4. **启动即可** — 无需 ETL，无需数据迁移

详细本体和映射编写指南可参考 [Ontop 官方文档](https://ontop-vkg.org/guide/)。

---

## License

MIT
