# Ontology Platform — Development Roadmap

> 基于当前代码库 (Spring Boot 3.4.3 + Ontop 5.5 + RDF4J 5.1.4) 的功能现状与扩展方向整理。
>
> 优先级: 🔴 高 (核心能力缺口) → 🟡 中 (产品化完善) → 🟢 低 (探索性)

---

## 🔴 Phase 1 — 核心扩展

- [x] **1.1 动态多租户管理 API**
  - 添加 `POST /api/v1/tenants` 创建租户（DB 持久化到 `tenants` 表）
  - 添加 `DELETE /api/v1/tenants/{id}` 下线租户
  - 添加 `PUT /api/v1/tenants/{id}` 更新租户配置
  - 支持运行时验证租户配置（OWL + OBDA + JDBC 连通性）

- [x] **1.2 动态 Schema 发现**
  - 替换 `OntologySchemaProvider` 的硬编码字符串
  - 从 OWL 文件解析类、属性、类层次（`OwlSchemaParser`）
  - 从 OBDA 文件解析映射、SQL 源表（`ObdaMappingParser`）
  - 供 LLM prompt 和 `/schema` 端点动态使用（`DynamicSchemaProvider`）
  - 新租户添加后自动生效，无需改 Java 代码

- [x] **1.3 持久化审计日志**
  - 将 `AuditService` 的 `CopyOnWriteArrayList` 替换为数据库存储
  - 建表 `audit_logs`（支持按租户、类型、时间范围过滤）
  - 添加保留策略配置（`retention-days`，默认 90 天，自动清理过期日志）

---

## 🟡 Phase 2 — 产品化完善

- [x] **2.1 API 认证与鉴权**
  - API Key 持久化到 `api_keys` 表（SHA-256 哈希，非明文存储）
  - JWT 登录 (`POST /api/v1/auth/login`) 返回 Bearer token
  - 双认证通道: `X-API-Key` header（DB 验证）或 `Authorization: Bearer`（JWT）
  - 三级 RBAC: `ROLE_ADMIN` > `ROLE_DEV` > `ROLE_READONLY`，`@PreAuthorize` 保护敏感端点
  - 启动时从 `application.yml` 自动 seed 静态 API Key 到 DB
  - 管理员 CRUD 管理 API Key 端点 (`GET/POST/PUT/DELETE /api/v1/api-keys`)
  - 可配置跳过认证（开发模式）

- [x] **2.2 SPARQL 结果格式多样化**
  - 根据 `Accept` header 返回 CSV / TSV / SPARQL XML / SPARQL JSON（`writeTupleResult` + RDF4J writer）
  - `SparqlResultFormatter` 统一转换逻辑，`SparqlResultFormat` 枚举管理 7 种格式
  - 保持现有 JSON 格式为默认

- [x] **2.3 NLQ 增强**
  - **数据驱动模板**: 11 个硬编码 regex 抽取为 YAML 外部配置（`nlq-templates/{tenantId}.yml`），`TemplateLoader` 加载，`SparqlTemplateGenerator` fallback
  - **LLM prompt 优化**: prompt 模板外部化为 `prompt-template.txt`，注入 few-shot 示例（`{tenantId}-examples.yml`），schema 格式带缩进类层次
  - **流式响应**: `GET /nlq/stream` SSE 端点 (`SseEmitter`)，分阶段推送 status → sparql → result → complete
  - **多轮对话**: `SessionManager` 组件（TTL 过期 + LRU 淘汰），会话历史注入 prompt

- [x] **2.4 集成测试** (Phase 1 已完成)
  - `PlatformIntegrationTest` (8 tests) — 覆盖租户 CRUD、schema 端点、审计日志全流程
  - `OwlSchemaParserTest` (6 tests) — 单元测试 OWL 解析
  - `ObdaMappingParserTest` (5 tests) — 单元测试 OBDA 解析
  - 共 65 个测试全部通过（含 NLQ 增强新增 29 个）

---

## 🟢 Phase 3 — SSE 硬化 + 探索性功能 ✅ 已完成

> 所有 41 个任务完成，含 Phase 5.1~5.3 补全。

- [x] **3.1 Auth Hardening (12/12)**
  - API Key 缓存 Caffeine 化（`ApiKeyService` + 可配置 TTL/max-size）
  - JWT 黑名单表 `jwt_blacklist` + `JwtBlacklistRepository` + 定时清理
  - `JwtAuthFilter` 请求时检查黑名单
  - Bucket4j 速率限制过滤器（login / reinit / audit-clear）
  - 认证失败审计日志（ApiKeyFilter / JwtAuthFilter / RateLimitFilter）
  - `${ADMIN_PASSWORD}` / `${JWT_SECRET}` / `${LLM_API_KEY}` 环境变量占位符
  - 默认密钥启动警告
  - 吊销端点（`POST /api-keys/{id}/revoke` + `POST /auth/revoke-all`）

- [x] **3.2 查询缓存 (7/7)**
  - Spring Cache (Caffeine) + `@Cacheable` 装饰 SPARQL 执行路径
  - Tenant-aware 缓存在 reinit 时自动清空
  - `POST /admin/cache/clear` + `GET /admin/cache/stats` 端点
  - `ontology.cache.*` 配置属性
  - ✅ Micrometer 缓存指标: `hit.count` / `miss.count` / `eviction.count` / `average.load.penalty`

- [x] **3.3 SQL DDL → OWL 自动生成 (4/4)**
  - `OwlGeneratorService` 读取 INFORMATION_SCHEMA → OWL Turtle
  - `POST /tenants/{id}/generate-owl` 端点
  - `ontology.owl-generation.*` 配置（含 `outputDir` / `enabled`）
  - ✅ 命名约定配置：singularize 修复（`statuses` → `Status`）、前缀、camelCase、PK `FunctionalProperty`

- [x] **3.4 联邦查询 (4/4)**
  - `FederatedQueryService` 解析 `SERVICE <tenant:{id}>` 模式
  - `CompletableFuture` 并发执行子查询
  - `TenantAccessEvaluator` 按租户 RBAC 校验（源租户 + 每个目标租户）
  - `perSubqueryTimeoutMs` 单独的每个子查询超时配置
  - 联邦查询指标 （`tenantId.federated` counter + timer）

- [x] **3.5 GraphQL 接口 (5/5)**
  - `schema.graphqls` + `SparqlResult` / `NlqResult` 类型 + JSON 标量
  - `GraphQLDataFetcher` + `@QueryMapping`
  - `/graphql` 端点受 Spring Security 保护

- [x] **3.6 本体可视化 (3/3)**
  - `OntologyGraphService` OWL → nodes/edges JSON
  - `GET /tenants/{id}/graph` 端点
  - 每租户 Caffeine 缓存

## 🟡 Phase 4 — 代码质量与合规

- [x] **4.1 代码合规扫描修复 (180 项问题)**
  - 为 25+ 个类添加 `@author` Javadoc
  - 为接口方法添加 `{@inheritDoc}` Javadoc（OntologyEngine / SchemaProvider）
  - 提取重复字符串字面量为常量（S1192）
  - 替换 `RuntimeException` 为 `OntologyPlatformException`（S112）
  - 抑制过多的构造参数警告（S107）
  - 移除未使用字段 / 局部变量 / 方法参数（S1068 / S1481 / S1172 / S1450）
  - 填充空 catch 块 / 空方法（S108 / S1186）
  - 降低认知复杂度：`DynamicSchemaProvider` 拆分为 5 个辅助方法（S3776）
  - 修复 `Optional` 未调用 `isPresent()` 直接 `.get()`（S3655）
  - 为类型通配符提供参数化类型（S3740）
  - 其他：`UnaryOperator<>`、缺失大括号、魔法数字、嵌套 if 合并

- [x] **4.2 补充缺失的测试** ✅ 已完成（7 个新测试类 + 扩展现有）
  - `RateLimitFilterTest` (4): tryConsume true/false + 不同 IP 独立桶
  - `JwtBlacklistRepositoryTest` (8): 增/查/过期/批量作废
  - `ApiKeyServiceTest` (10): 缓存、失效、过期 key 修复
  - `JwtAuthFilterTest` (6): 黑名单、过期/无效/有效 token、租户域
  - `OwlGeneratorServiceTest` (+5): camelCase、前缀、PK、空 DB、边缘用例
  - `QueryCacheTest` (+1): 并发访问场景

## 🟢 Phase 5 — 探索性方向

- [x] **5.1 缓存 Micrometer 指标** (Phase 3.2.5 延续) — ✅ 已完成（hit.count / miss.count / eviction.count / average.load.penalty）
- [x] **5.2 联邦查询 RBAC + 超时/并发限制** (Phase 3.4 延续) — ✅ 已完成（`TenantAccessEvaluator` + `perSubqueryTimeoutMs` + 指标）
- [x] **5.3 OWL 生成命名约定配置** (Phase 3.3 延续) — ✅ 已完成（singularize 修复、`outputDir`/`enabled` 属性、PK `FunctionalProperty`）
- [x] **5.4 前端本体可视化仪表盘** (基于 Phase 3.6 graph 端点)
  - ✅ 静态 HTML 页面：vis-network CDN、租户选择器、节点搜索
  - ✅ `/ontology-viz/**` 匿名访问白名单
  - ⏳ 手动验证页面渲染和交互 (4 项)
- [x] **5.5 OpenTelemetry 分布式追踪**
  - ✅ 依赖：`micrometer-tracing-bridge-otel`、`opentelemetry-exporter-otlp`、`spring-boot-starter-aop`
  - ✅ 配置：`management.tracing.sampling.probability` + OTLP endpoint
  - ✅ `@Observed` 注解：`SparqlController`、`NaturalLanguageQueryService`、`FederatedQueryService`、`CachedSparqlService`
  - ✅ `TracingConfig`：`ObservedAspect` 注册，149 测试通过
  - ⏳ 手动验证 OTel Agent + 端点 (1 项)

---

## 完成标记

| 日期 | 项目 | 备注 |
|------|------|------|
| 2026-07-02 | Phase 1 核心扩展 | 动态租户 API、动态 Schema 发现、持久化审计日志全部完成。27/27 测试通过。归档为 `phase1-core-extensions` |
| 2026-07-02 | Phase 2.2 SPARQL 结果格式多样化 | CSV / TSV / SPARQL XML / SPARQL JSON 格式支持完成。共 36 个测试通过。 |
| 2026-07-02 | Phase 2.3 NLQ 增强 | 数据驱动模板、LLM prompt 优化、SSE 流式响应、多轮对话完成。共 65 个测试通过。 |
| 2026-07-02 | Phase 2.1 API 认证与鉴权 | API Key 持久化 + JWT + RBAC + CRUD 端点 + 9 个集成测试。共 71 个测试通过。 |
| 2026-07-03 | Phase 3 SSE 硬化 + 探索性功能 | 38/41 任务完成。Auth Hardening (12/12)、查询缓存 (6/7)、OWL 生成 (3/4)、联邦查询 (4/4)、GraphQL (5/5)、本体可视化 (3/3)。共 103 个测试通过。 |
| 2026-07-03 | Phase 4.1 代码合规修复 | 修复 CSV 扫描 180 项问题：@author 标签、重复字符串常量、泛型异常、未使用字段等。编译与全部 103 测试通过。 |
| 2026-07-03 | Phase 2 产品化完善归档 | SPARQL 格式协商、NLQ YAML 模板、LLM prompt 优化、流式+多轮对话、API Key DB 持久化、交叉测试全部完成。归档为 `phase2-productization`。 |
| 2026-07-03 | Phase 5.2 联邦查询 RBAC 强化 | `TenantAccessEvaluator` + 按租户 API 作用域 + per-sub-query 超时 + 联邦查询指标 + 12 个新测试。归档为 `federated-query-rbac`。共 115 个测试通过。 |
| 2026-07-03 | Polish & Tests 收尾 | 缓存指标 (5.1)、OWL 命名 (3.3/5.3)、34 个新测试：`RateLimitFilterTest`(4)、`JwtBlacklistRepositoryTest`(8)、`ApiKeyServiceTest`(10)、`JwtAuthFilterTest`(6)、+OwlGeneratorServiceTest 扩展(5)、+QueryCacheTest 扩展(1)。修复 `ApiKeyService.validateKey` 过期 key 返回 bug。共 **149 个测试，0 失败**。 |
| 2026-07-03 | Phase 5.4 本体可视化仪表盘 + Phase 5.5 OTel 追踪 (代码完成) | 前端页面 `ontology-viz/index.html` + vis-network CDN + 安全白名单 (2/6 代码)。OTel 追踪：`pom.xml` 依赖、`application.yml` 配置、`@Observed` 注解覆盖 SPARQL/NLQ/联邦查询、`TracingConfig` + `ObservedAspect` 注册 (8/9 代码)。149 测试全部通过。待手动验证仪表盘渲染和 OTel 端点。 |
