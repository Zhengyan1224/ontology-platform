# 未来方向探索

> 基于当前代码库 (Spring Boot 3.4.3 + Ontop 5.5 + RDF4J 5.1.4, 155 测试全部通过) 整理的后续待办方向和优化思路。
>
> 生成日期: 2026-07-03

---

## 目录

- [优先级标记说明](#优先级标记说明)
- [方向一：待收尾的手动验证项](#方向一待收尾的手动验证项)
- [方向二：核心功能缺口](#方向二核心功能缺口)
- [方向三：生产化差距](#方向三生产化差距)
- [方向四：OBDA 自动生成的后续](#方向四obda-自动生成的后续)
- [方向五：架构打磨与技术债务](#方向五架构打磨与技术债务)
- [方向六：测试能力建设](#方向六测试能力建设)
- [总览全景图](#总览全景图)

---

## 优先级标记说明

| 标记 | 含义 |
|------|------|
| 🔴 P0 | 高优先级 — 核心能力缺口或阻塞项 |
| 🟡 P1 | 中优先级 — 产品化完善 |
| 🟢 P2 | 低优先级 — 探索性/锦上添花 |

改动量估算：

| 标记 | 含义 |
|------|------|
| S | Small — 1~3 个文件, 几天 |
| M | Medium — 3~8 个文件, 一周左右 |
| L | Large — 8+ 个文件, 两周以上 |
| XL | Extra Large — 跨模块系统性改动 |

---

## 方向一：待收尾的手动验证项

> 这几项「就差最后一公里」，做完可以直接归档 `ontology-visualization-dashboard` 和 `opentelemetry-tracing` 两个 change。

### 1.1 本体可视化仪表盘手动验证 🟢 P2 / S

**当前状态**：`ontology-viz/index.html` 静态页面代码完成，4 项待手动验证。

**验证清单**：

| # | 验证项 | 预期结果 |
|---|--------|----------|
| 1 | 页面加载 | `http://localhost:8080/ontology-viz/` 正常渲染 vis-network 画布 |
| 2 | 租户选择器 | 下拉框列出 sample / university 两个租户，切换后图数据刷新 |
| 3 | 节点搜索 | 输入类名关键字，高亮匹配节点 |
| 4 | 交互操作 | 拖拽、缩放、点击节点显示详情 |

**方案**：启动服务 → 逐项验证 → 修复发现的问题。

**依赖**：无，纯手动操作。

### 1.2 OpenTelemetry 端点验证 🟢 P2 / S

**当前状态**：`@Observed` 注解 + Micrometer Tracing + OTLP 导出代码完成。

**验证清单**：

| # | 验证项 | 预期结果 |
|---|--------|----------|
| 1 | Jaeger 收到 span | `docker run jaegertracing/all-in-one` 后，启动应用加 `-javaagent:opentelemetry-javaagent.jar`，SPARQL/NLQ 请求的 span 出现在 Jaeger UI |

**方案**：
1. `docker run -p 16686:16686 -p 4318:4318 jaegertracing/all-in-one:latest`
2. `mvn spring-boot:run -Dspring-boot.run.jvmArguments="-javaagent:path/to/opentelemetry-javaagent.jar"`
3. 发出请求 → 检查 Jaeger UI (`http://localhost:16686`)

**注意**：AGENTS.md 已记录了相关说明，`application.yml` 中 OTLP 端点也已配置。

---

## 方向二：核心功能缺口

### 2.1 支持 SPARQL ASK 查询 🟡 P1 / S

**现状**：`OntopEngine.executeQuery()` 只处理了 tuple query (SELECT) 和 graph query (CONSTRUCT/DESCRIBE)，`SparqlQueryResult` 没有 `boolean` 类型，收到 ASK 会走到默认分支。

```
SparqlController
  └─ cachedSparqlService.executeQuery()
       └─ OntopEngine.executeQuery()
            ├─ TupleQueryResult  ← SELECT
            ├─ GraphQueryResult  ← CONSTRUCT / DESCRIBE
            └─ ???              ← ASK → 未处理
```

**方案**：
1. `SparqlQueryResult` 增加 `booleanQueryResult` 字段和 `BooleanQueryResult` 模式
2. `OntopEngine.executeQuery()` 增加 `queryType == BooleanQuery` 的判断分支，执行 `BooleanQuery.evaluate()`
3. `SparqlResultFormatter` 增加 ASK 结果的序列化（JSON 返回 `true`/`false`）
4. 前端 API 返回格式对齐

**改动量**：~4 个文件，低风险，可独立发布。

### 2.2 SPARQL UPDATE / INSERT / DELETE 支持 🔴 P0 / M~L

**现状**：平台完全是只读架构。

```
当前:
  POST /tenants/{id}/sparql
  → 只执行 SELECT / CONSTRUCT / DESCRIBE
  → 收到 UPDATE 会执行但没有写路径

需要:
  POST /tenants/{id}/sparql-update
  → 执行 SPARQL UPDATE
  → 引擎将 UPDATE 翻译为 SQL INSERT/UPDATE/DELETE
  → 直接修改底层数据库
```

**关键不确定因素**（需要先做 spike）：

| 问题 | 说明 |
|------|------|
| Ontop 支持 UPDATE 吗？ | Ontop 5.x 的 SPARQL 更新支持有限，需要验证 `UpdateExecution` 的行为 |
| 反向映射 | 从 RDF 三元组到关系行的映射是反向的 OBDA，Ontop 是否支持？ |
| 事务 | 如果支持，一个 UPDATE 跨多个表时的事务边界怎么处理？ |
| 安全 | 写操作必须有更严格的鉴权（至少需要 `ROLE_DEV` 以上） |

**建议方案（如果 Ontop 支持）**：
1. `SparqlController` 增加 `POST /tenants/{id}/sparql-update` 端点
2. `OntopEngine` 增加 `executeUpdate(tenantId, sparql)` 方法，调用 `UpdateExecution.execute()`
3. SecurityConfig 中限制该端点需要 `ROLE_ADMIN` 或 `ROLE_DEV`
4. 审计日志明确记录写操作

**依赖**：需要先做 spike 验证 Ontop 的 SPARQL UPDATE 支持程度。

**改动量**：如果 Ontop 天生支持，~5 个文件；如果不支持，需要更深入的研究，可能是 XL 级别。

### 2.3 OWL 推理增强 🟢 P2 / M

**现状**：University 租户演示了 `rdfs:subClassOf` 和 `rdfs:subPropertyOf` 推理，这由 Ontop/RDF4J 的推理层处理。但缺乏更深的 OWL DL 推理。

**可考虑方向**：

| 推理类型 | 当前支持 | 价值 |
|----------|----------|------|
| RDFS (subClassOf, subPropertyOf, domain, range) | ✅ | 基础 |
| OWL (equivalentClass, disjointWith, intersectionOf, unionOf) | ❌ | 中等 |
| OWL 2 RL (基于规则的 OWL 推理) | ❌ | 高 |
| SWRL 规则推理 | ❌ | 取决于场景 |

**方案**：
1. 评估是否需要更深的推理（取决于业务场景）
2. 如果需要，可集成 OWL API 的 reasoner（已在 classpath 上）或使用 RDF4J 的推理 Sail

**建议**：在需要之前保持现状，RDFS 推理对大多数 OLAP 场景已足够。

---

## 方向三：生产化差距

### 3.1 Docker 化 🟡 P1 / M

**现状**：无 Dockerfile，只能用 `mvn spring-boot:run` 本地启动。

**方案**：
```
项目根目录
├── Dockerfile
│   ├── Stage 1: maven:3-eclipse-temurin-21 编译
│   ├── Stage 2: eclipse-temurin-21-jre 运行
│   └── 暴露 8080 端口
├── docker-compose.yml
│   ├── app (本应用)
│   ├── jaeger (可选, 用于 OTel 验证)
│   └── postgres (可选, 未来替代 H2)
└── .dockerignore
```

**要点**：
- 多阶段构建减镜像体积
- `application.yml` 中的 OTLP 端点通过环境变量覆盖
- secrets (JWT_SECRET, ADMIN_PASSWORD, LLM_API_KEY) 通过 Docker secrets 或环境变量传入

**改动量**：3 个新文件，无代码改动。

### 3.2 PostgreSQL / 外部数据库支持 🟡 P1 / M

**现状**：默认开发配置已改为 H2 文件库（`./data/ontology-platform`），重启后数据会保留；但生产环境仍建议迁移到 PostgreSQL 等外部数据库，以获得备份、权限、多实例和运维能力。

**建议方案**：

```
platform 数据 (tenants, api_keys, audit_logs, jwt_blacklist)
├── 迁移到 PostgreSQL
├── 表结构已定义在 init-*.sql 中，语法基本兼容
├── 通过 application-prod.yml 切换 profile
└── 使用 HikariCP 连接池

tenant 数据 (业务数据库)
├── 每个租户的 JDBC URL 指向各自的数据库
├── 可以是 PostgreSQL / MySQL / SQL Server
├── 需要 Testcontainers 集成测试验证
└── 已有 Tenant.jdbcDriver 字段，理论上已支持
```

**阻碍因素**：
- pom.xml 中没有 PostgreSQL / MySQL JDBC 驱动
- 所有测试都依赖 H2，没有外部 DB 的集成测试
- `init-*.sql` 已尽量使用 H2/PostgreSQL 兼容语法；切换到其他数据库时仍需要验证 JDBC driver、标识符大小写、索引语法和初始化策略

**改动量**：M (~6 个文件: pom.xml + application-prod.yml + Docker Compose + 驱动适配 + 测试)

### 3.3 数据库迁移工具 🟡 P1 / M

**现状**：表结构通过 `src/main/resources/db/init-*.sql` 在启动时执行，无版本控制。当表结构变更时，需要手动处理。

**方案对比**：

| 工具 | 优势 | 劣势 |
|------|------|------|
| **Flyway** | 更轻量, SQL 原生, Spring Boot 自动配置 | 需要在 `application.yml` 中配置 `spring.flyway.*` |
| **Liquibase** | 支持多种格式 (XML/YAML/JSON/SQL) | 配置更重 |

**建议**：采用 Flyway，因为：
- Spring Boot 3.4 对其有原生支持
- 可以直接复用现有的 `init-*.sql` 文件作为 V1 迁移
- 学习成本低

**迁移步骤**：
1. pom.xml 添加 `flyway-core` + `flyway-database-postgresql`
2. 将现有的 `init-*.sql` 整理为 `V1__init.sql` 统一迁移文件
3. 配置 `spring.flyway.*`
4. 启动时验证

**改动量**：M (~5 个文件: pom.xml + application.yml + 迁移脚本 + 测试)

### 3.4 密码与密钥管理 🟡 P1 / S

**现状**：`application.yml` 中有明文默认密码和密钥。

```yaml
ontology:
  auth:
    jwt-secret: default-jwt-secret-key
    admin-password: admin123
  nlq:
    llm:
      api-key: sk-placeholder
```

**当前已做的**：支持 `${JWT_SECRET}`、`${ADMIN_PASSWORD}`、`${LLM_API_KEY}` 环境变量覆盖。

**可改进**：
1. **启动时严格模式**：检测到仍在使用默认值（而非环境变量）时，启动失败而非仅警告
2. **Docker secrets 支持**：读取 `/run/secrets/` 下的文件
3. **Vault 集成**（可选）：如果已有 HashiCorp Vault 基础设施

**方案**：
1. `OntologyInitializer` 或新的 `SecretsValidator` 在 `@PostConstruct` 中检查所有密钥是否被覆盖
2. 如果任意一个仍是默认值且 `ontology.auth.strict-mode: true`，则抛出异常阻止启动
3. Docker Compose 中演示 secrets 用法

**改动量**：S (~2 个文件)

### 3.5 CI/CD 流水线 🟡 P1 / M

**现状**：无 CI/CD，全靠本地 `mvn test` + `mvn spring-boot:run`。

**建议方案（GitHub Actions）**：

```yaml
# .github/workflows/ci.yml
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: mvn test
  build:
    needs: test
    steps:
      - run: mvn package -DskipTests
      # 可选: 构建 Docker 镜像并推送
```

**额外可加的**：

| 阶段 | 说明 |
|------|------|
| `mvn test` | 单元测试 + 集成测试 |
| `mvn verify` | 含集成测试 |
| 代码质量 | 可选集成 SonarCloud / Qodana |
| Docker build | 多阶段构建 + push 到 registry |
| 部署 | 可选: SSH deploy / Kubernetes |

**改动量**：S (~1 个 `.github/workflows/ci.yml` 文件)

---

## 方向四：OBDA 自动生成的后续

### 4.1 映射验证器 🟡 P1 / S~M

**现状**：`POST /tenants/{id}/generate-mapping` 生成的 `.obda` 文件直接打包成 ZIP，没有在服务端验证 Ontop 是否能正确加载和解析。

**方案**：

```java
public class ObdaMappingValidator {
    public ValidationResult validate(String obdaContent, Tenant tenant) {
        // 1. 使用 Ontop 的 OBDAParser 解析 OBDA 内容
        // 2. 检查所有引用的表和列在目标 DB 中存在
        // 3. 检查所有 IRI 模板格式正确
        // 4. 检查映射是否完整（每个映射都有 target + source）
        // 5. 返回 ValidationResult(valid, errors, warnings)
    }
}
```

**验证项**：

| 验证 | 说明 |
|------|------|
| 语法 | OBDA 文件能被 Ontop 的 OBDAParser 正确解析 |
| 表存在 | SQL SELECT 中引用的表在 DB 中确实存在 |
| 列存在 | SELECT 列和 target 中的 `{col}` 在表中有对应列 |
| PK 完整 | 每个映射都有正确的 PK 列 |
| IRI 格式 | `{column}` 占位符在 target 中正确使用 |

**改动量**：M (~3 个新文件 + 测试)

### 4.2 差异更新/增量同步 🟢 P2 / L

**现状**：DB 表结构变更后，需要重新生成整个 OBDA 文件，然后手动对比差异。

**方案**：基于 `JdbcMetadataReader` 的快照对比

```
1. 生成一个「metadata 快照」(表名 + 列名 + 类型 + PK/FK 的摘要)
2. 每次 `generate-mapping` 时对比当前快照和上次生成的快照
3. 输出增量变更: ADD TABLE books / DROP COLUMN title / MODIFY COLUMN id TYPE BIGINT
4. 可选: 自动输出 OBDA diff
```

**价值**：当表结构频繁变化时（开发阶段），增量更新比全量替换更安全。

**改动量**：L (需要快照存储 + 对比逻辑 + 增量输出 + 测试)

### 4.3 NLQ 模板联动生成 🟢 P2 / M

**现状**：OBDA 映射和 NLQ YAML 模板是独立生成的。添加新租户时，需要手动编写 NLQ 模板规则。

**方案**：`generate-mapping` 端点同时输出 NLQ YAML 模板骨架：

```yaml
# generated-nlq-template.yml
rules:
  - patterns:
      - "list.*(all\\s+)?books?"
      - "show me all books"
    sparql: |
      PREFIX : <http://ontology.zhengyan.org/ontology/newtenant#>
      SELECT ?book ?title WHERE { ?book a :Book . ?book :title ?title . }
    description: "List all books"
  - patterns:
      - "list.*authors?"
    sparql: |
      PREFIX : <http://ontology.zhengyan.org/ontology/newtenant#>
      SELECT ?author ?name WHERE { ?author a :Author . ?author :name ?name . }
    description: "List all authors"
```

**自动生成规则**：
- 每个类 → 一条 "list all" 规则
- 每条字符串属性 → 一条 "find by name" 规则
- 模板使用配置化的命名空间

**改动量**：M (~2 个文件: 生成器 + 测试)

### 4.4 扩展到 R2RML 输出 🟢 P2 / M

**现状**：只输出 Ontop 原生 OBDA 格式。R2RML 是 W3C 标准格式，某些场景需要。

**方案**：在 `ObdaGeneratorService` 或通过策略模式增加 R2RML 导出：

```
generateObda(tenant)       → Ontop 原生 .obda 格式
generateR2RML(tenant)      → W3C 标准 .ttl 格式 (R2RML)
generateAll(tenant)        → ZIP 包含 .owl + .obda + .ttl
```

**改动量**：M (~3 个文件)

---

## 方向五：架构打磨与技术债务

### 5.1 缓存逐出逻辑 🟡 P1 / S

**现状**：`CachedSparqlService.java:40` 有一个 TODO。

```java
// TODO: implement cache eviction logic
// Currently the cache entry expires based on TTL configuration.
// We should manually evict cache entries related to a tenant when:
// - The tenant is reinitialized
// - The tenant's configuration is updated
```

实际上 tenant reinit 时已经清空了整个缓存。这个 TODO 可能指的是更精细的逐出策略（比如按租户前缀逐出）。

**建议**：如果当前按 TTL 的策略工作正常，可以移除 TODO 或在 `CacheConfig` 中增加显式的按租户逐出方法。

**改动量**：S (1~2 个文件)

### 5.2 错误处理规范化 🔴 P0 / M

**现状**：`ObdaGeneratorService.generateObda()` 抛出 `throws Exception`，`AdminController.generateMapping()` 也没有对 OBDA 生成过程中的异常做专门处理。

**检查发现**：

| 问题 | 位置 | 建议 |
|------|------|------|
| `throws Exception` | `ObdaGeneratorService.java:24` | 改为具体异常类型 |
| SQL 异常暴露 | `generateObda` → `DriverManager.getConnection` | 包装为 `OntologyPlatformException` |
| 无生成错误提示 | `AdminController.generateMapping()` | 增加 try-catch，返回 422 + 错误信息 |

**方案**：
1. 定义 `ObdaGenerationException extends OntologyPlatformException`
2. `ObdaGeneratorService` 捕获 `SQLException` 并包装
3. `AdminController` 处理异常，返回结构化错误 JSON

**改动量**：M (~4 个文件)

### 5.3 Controller 拆分 🟢 P2 / M

**现状**：`AdminController` 承载了多种职责。

```
AdminController.java
├── cache 管理 (POST clear, GET stats)
├── OWL 生成 (POST generate-owl) ← @Deprecated
├── OBDA 生成 (POST generate-mapping)
├── 审计日志 (GET, POST clear)
└── (未来可能更多)
```

**建议拆分**：

```
├── AdminController.java              → 仅管理端点 (cache, health)
├── OntologyGenerationController.java → generate-owl, generate-mapping
└── AuditController.java              → audit-log 端点
```

**改动量**：M (~4 个文件，不改变业务逻辑，纯重构)

### 5.4 统一分页 🔴 P0 / M

**现状**：SPARQL SELECT 查询有可能返回数十万行结果，当前没有分页机制。

**方案**：

```java
// 方式一: SPARQL LIMIT/OFFSET (推荐)
// 由用户在查询中自行控制
SELECT ?s ?p ?o WHERE { ... } LIMIT 100 OFFSET 0

// 方式二: 服务端默认限制
// SparqlController 对所有非 LIMIT 查询添加默认 LIMIT
// 配置: ontology.sparql.max-results: 10000

// 方式三: 游标式分页
// 对超大结果集，提供 next 令牌
POST /tenants/{id}/sparql
{"query": "...", "page": {"size": 100, "token": "..." }}
```

**建议组合**：方式一 + 方式二（硬上限保护）。

**改动量**：S (~3 个文件: 配置 + 拦截器 + 测试)

---

## 方向六：测试能力建设

### 6.1 Testcontainers 集成测试 🟡 P1 / L

**现状**：所有测试使用 H2 内存数据库，无法验证 PostgreSQL/MySQL 兼容性。

**依赖**：pom.xml 需要添加 `testcontainers` 和 `testcontainers-postgresql`。

**建议范围**：

| 测试类别 | 说明 |
|----------|------|
| Repository 测试 | `TenantPersistenceService` + `ApiKeyRepository` 在 PostgreSQL 上的行为 |
| 引擎测试 | Ontop 引擎连接到真实 PostgreSQL 时的表现 |
| 生成测试 | `ObdaGeneratorService` 在 PostgreSQL INFORMATION_SCHEMA 上的输出 |
| 集成测试 | 完整的 SPARQL 查询 + 结果返回 + NLQ 在 PostgreSQL 后端 |

**改动量**：L (pom.xml 依赖 + `AbstractIntegrationTest` 基类 + 多个测试类改造)

### 6.2 性能与压力测试 🟢 P2 / M

**现状**：不知道平台能承受多少 QPS，也不知道瓶颈在哪里。

**建议工具**：

| 工具 | 用途 |
|------|------|
| **JMeter** | 混合场景压测 (SPARQL + NLQ + API Key auth) |
| **Async Profiler** | CPU/内存热点分析 |
| **Micrometer 已有指标** | cache hit/miss, 联邦查询耗时, rate limit 命中 |

**建议压测场景**：

```
场景 1: SPARQL 并发 (100 并发, 持续 5 分钟)
场景 2: NLQ 混合负载 (SPARQL 80% + NLQ 20%)
场景 3: 大结果集 (返回 10000+ 行的 SPARQL)
场景 4: 联邦查询 (跨 2~3 个租户的 SERVICE 查询)
```

**可观测的指标**：
- 吞吐量 (QPS)
- P50/P95/P99 延迟
- 内存占用 (堆 + 直接内存)
- GC 暂停时间
- 缓存命中率变化

**改动量**：M (测试脚本 + 测试数据, 无代码改动)

### 6.3 测试覆盖率门禁 🟢 P2 / S

**建议**：在 CI 中配置 JaCoCo 覆盖率门禁（但需要注意，JaCoCo 可能和 Java 26 兼容）。

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <configuration>
        <rules>
            <rule>
                <element>BUNDLE</element>
                <limits>
                    <limit>
                        <counter>INSTRUCTION</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.60</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</plugin>
```

**注意**：需要验证 JaCoCo 在 Java 26 下的兼容性。

**改动量**：S (~2 个文件: pom.xml + CI 配置)

---

## 总览全景图

```
优先级        S (小)                 M (中)                    L (大)
─────       ───────              ─────────               ─────────
🔴 P0                             5.2 错误处理规范化        2.2 SPARQL UPDATE
                                  5.4 统一分页               (先 spike)
                                  
🟡 P1      3.4 密码管理           3.1 Docker 化             6.1 Testcontainers
           5.1 缓存 TODO          3.2 PostgreSQL 支持
                                  3.3 DB 迁移工具
                                  3.5 CI/CD
                                  4.1 映射验证器

🟢 P2      1.1 仪表盘验证         2.3 OWL 推理增强          4.2 差异更新
           1.2 OTel 验证          4.3 NLQ 模板联动
           6.3 覆盖率门禁          4.4 R2RML 输出
                                  5.3 Controller 拆分
                                  6.2 性能测试

改动量总计  5 × S                  10 × M                   2 × L
```

### 推荐执行顺序

```
第一批（快速见效）
├── 1.1 仪表盘验证 (S)
├── 1.2 OTel 验证 (S)
├── 5.1 缓存 TODO (S)
├── 3.4 密码管理 (S)
└── 5.4 统一分页 (S)

第二批（核心能力补全）
├── 4.1 映射验证器 (M)
├── 5.2 错误处理规范化 (M)
└── 3.5 CI/CD (M)

第三批（生产化）
├── 3.1 Docker 化 (M)
├── 3.2 PostgreSQL 支持 (M)
└── 3.3 DB 迁移工具 (M)

第四批（探索性）
├── 2.2 SPARQL UPDATE (L，先 spike)
├── 4.2 差异更新 (L)
├── 4.3 NLQ 模板联动 (M)
└── 6.1 Testcontainers (L)
```

> 以上优先级和建议仅基于代码库分析和常见模式。实际情况取决于你的业务需求和使用场景。
