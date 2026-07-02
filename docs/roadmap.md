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

- [ ] **2.1 API 认证与鉴权**
  - 引入 Spring Security
  - 支持 API Key (Header) 或 JWT 认证
  - 为敏感端点（reinit、audit-log、clear）添加权限控制
  - 可配置跳过认证（开发模式）

- [ ] **2.2 SPARQL 结果格式多样化**
  - 根据 `Accept` header 返回 SPARQL XML / CSV / TSV / JSON-LD
  - `CONSTRUCT` 查询返回 Turtle 格式
  - 保持现有 JSON 格式为默认

- [ ] **2.3 NLQ 增强**
  - **数据驱动模板**: 将 12 个硬编码 regex 改为 YAML/JSON 配置，每个租户可自定义
  - **LLM prompt 优化**: 加入 few-shot 示例、更清晰的 schema 描述
  - **流式响应**: NLQ 结果通过 SSE 逐步输出
  - **多轮对话**: 在上下文中保存历史，支持追问

- [x] **2.4 集成测试** (Phase 1 已完成)
  - `PlatformIntegrationTest` (8 tests) — 覆盖租户 CRUD、schema 端点、审计日志全流程
  - `OwlSchemaParserTest` (6 tests) — 单元测试 OWL 解析
  - `ObdaMappingParserTest` (5 tests) — 单元测试 OBDA 解析
  - 共 27 个测试全部通过（14 个原有 + 13 个新增）

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
