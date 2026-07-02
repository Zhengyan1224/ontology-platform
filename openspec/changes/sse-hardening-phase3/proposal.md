## Why

Phase 2 的 NLQ SSE 流式响应已实现（`GET /tenants/{id}/nlq/stream` + `SseEmitter`），但新引入的 API 认证系统存在生产化缺口（缓存无淘汰、JWT 无吊销、密钥明文、无鉴权审计）。同时平台需要向 Phase 3 探索性功能延伸。

## What Changes

### B. Auth 加固 (Phase 2 收尾)
- `ApiKeyService` 内存缓存添加淘汰策略（TTL + 大小限制）防止 OOM
- JWT 黑名单机制，支持在 token 过期前吊销
- `admin-password` 和 `jwt.secret` 支持环境变量注入，降低明文泄露风险
- 鉴权失败事件写入 `audit_logs` 表
- 请求频率限制（Rate Limiting）保护敏感端点

### C. Phase 3 入口
- **查询缓存**: Caffeine 缓存重复 SPARQL 查询结果，可配置 TTL 和最大条目
- **SQL DDL → OWL 自动生成**: 读取 DB `INFORMATION_SCHEMA` 生成初步 OWL 本体
- **联邦查询**: 支持 SPARQL `SERVICE <tenant:xxx>` 语法跨租户查询
- **GraphQL 接口**: 新增 `/api/v1/graphql` 端点封装 SPARQL
- **本体可视化**: 新增 `GET /api/v1/tenants/{id}/graph` 端点

## Capabilities

### New Capabilities
- `auth-hardening`: 认证系统安全加固（缓存淘汰、JWT 吊销、密钥管理、审计、频率限制）
- `query-caching`: SPARQL 查询结果缓存层（Caffeine，可配置 TTL/大小）
- `sql-ddl-to-owl`: 从数据库 DDL 自动生成 OWL 本体文件
- `federated-query`: 跨租户 SPARQL 联邦查询
- `graphql-interface`: GraphQL API 封装层
- `ontology-visualization`: 本体结构图数据 API

### Modified Capabilities
- *(none)*

## Impact

- **新增依赖**: `spring-boot-starter-graphql` (GraphQL)、`spring-cache` + `caffeine` (缓存)、`bucket4j` (限流)
- **API 新增**: GraphQL 端点、本体可视化端点、联邦查询端点
- **API 变更**: 敏感端点增加频率限制（非 breaking）
- **配置新增**: `ontology.cache.*`、`ontology.ratelimit.*`、`ontology.jwt.blacklist.*`
- **文档**: 需要更新 API 文档说明新端点和限流规则
