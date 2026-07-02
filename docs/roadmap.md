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

## 🟢 Phase 3 — 探索性功能

- [ ] **3.1 GraphQL 接口**
  - 引入 `graphql-spring-boot-starter`
  - 将 SPARQL 查询封装为 GraphQL 查询/mutation
  - 前端调用更轻量

- [ ] **3.2 本体可视化**
  - 添加 `/api/v1/tenants/{id}/graph` 端点返回本体结构
  - 前端使用 D3.js / vis.js 展示类层次、属性关系
  - 点击节点可查看实例数据

- [ ] **3.3 查询缓存**
  - 引入 Spring Cache (Caffeine/Redis)
  - 对相同 SPARQL 查询缓存结果
  - 考虑缓存失效策略（定时过期 + 手动清空）

- [ ] **3.4 SQL DDL → OWL 自动生成**
  - 读取数据库 INFORMATION_SCHEMA
  - 自动生成初步 OWL 本体（表→类，列→属性，外键→对象属性）
  - 降低新租户接入成本

- [ ] **3.5 联邦查询**
  - 支持在 SPARQL 中使用 `SERVICE <tenant:xxx>` 语法
  - 跨多个租户/数据库做联合查询

---

## 完成标记

| 日期 | 项目 | 备注 |
|------|------|------|
| 2026-07-02 | Phase 1 核心扩展 | 动态租户 API、动态 Schema 发现、持久化审计日志全部完成。27/27 测试通过。归档为 `phase1-core-extensions` |
| 2026-07-02 | Phase 2.2 SPARQL 结果格式多样化 | CSV / TSV / SPARQL XML / SPARQL JSON 格式支持完成。共 36 个测试通过。 |
| 2026-07-02 | Phase 2.3 NLQ 增强 | 数据驱动模板、LLM prompt 优化、SSE 流式响应、多轮对话完成。共 65 个测试通过。 |
| 2026-07-02 | Phase 2.1 API 认证与鉴权 | API Key 持久化 + JWT + RBAC + CRUD 端点 + 9 个集成测试。共 71 个测试通过。 |
