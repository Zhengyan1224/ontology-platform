## Context

The platform currently has:

- **Auth**: API Key DB persistence + JWT login + RBAC (3 roles) — just built, functional but missing production guards
- **NLQ**: SSE streaming (`GET /tenants/{id}/nlq/stream`) already implemented with `SseEmitter` + `CompletableFuture`
- **Phase 1**: Dynamic tenants, schema discovery, audit logs — stable

This change addresses the auth production gaps and extends into Phase 3 capabilities.

## Goals / Non-Goals

**Goals:**
- Add cache eviction, JWT revocation, rate limiting, and auth audit to the auth system
- Support environment-variable-based secrets injection
- Implement SPARQL result caching with Caffeine
- Add GraphQL endpoint wrapping SPARQL queries
- Generate OWL ontologies from SQL DDL schemas
- Support cross-tenant federated SPARQL queries
- Expose ontology structure as JSON graph data

**Non-Goals:**
- SSE streaming (already done in a prior change)
- User registration UI or self-service
- OAuth2 / SSO integration
- Persistent session storage (sessions stay in-memory)
- SPARQL UPDATE / INSERT / DELETE support via GraphQL

## Decisions

### D1: JWT Blacklist via DB table (not Redis)
- **选择**: `jwt_blacklist` 表存储已吊销的 JWT `jti`，`JwtAuthFilter` 每次请求检查
- **原因**: 系统已有 H2/DB，不引入新依赖。`jwt_blacklist` 表 TTL 定期清理过期条目
- **替代方案**: Redis — 性能更好但增加运维复杂度，当前规模不需要

### D2: Rate Limiting with Bucket4j (not Spring Gateway)
- **选择**: Bucket4j — 轻量级令牌桶，纯 Java 无额外依赖框架
- **原因**: 只在少数敏感端点生效，Bucket4j 可嵌入 Filter，不影响现有 filter chain
- **替代方案**: Spring Cloud Gateway — 太重，需要重构入口

### D3: Caffeine for SPARQL caching (not Redis)
- **选择**: Caffeine (Spring Cache + Caffeine)
- **原因**: 查询结果缓存是本地缓存，无需分布式。Caffeine 是 Spring Boot 默认缓存实现，零配置启动。支持 size-based eviction + TTL
- **替代方案**: Redis — 如需多实例共享缓存在未来升级

### D4: GraphQL via spring-graphql + graphql-java
- **选择**: `spring-boot-starter-graphql` 自动配置 schema-first GraphQL 端点
- **原因**: Spring 官方支持，自动集成 Spring Security（可复用现有 `@PreAuthorize`），schema-first 方式与现有 SPARQL 查询语义对齐
- **替代方案**: 自建 REST 封装 — 无法享受 GraphQL 的按需查询优势

### D5: OWL Generation as API + file output
- **选择**: 启动时可选执行 + 手动触发 API (`POST /api/v1/tenants/{id}/generate-owl`)
- **原因**: DDL→OWL 映射需要人工审查和调整，全自动不可靠。API 触发 + 文件输出到 `src/main/resources/ontologies/`，供用户修改后通过租户配置引用

### D6: Federated Query via SPARQL SERVICE extension
- **选择**: 自定义 `SERVICE <tenant:{id}>` URI 方案，在 Ontop engine 层拦截并路由到目标租户的 DataSource
- **原因**: 保持 SPARQL 标准语法，对用户透明。解析 `SERVICE` 子句后动态创建临时查询
- **替代方案**: 预先配置的联合视图 — 不够灵活

### D7: Environment variable for secrets
- **选择**: `application.yml` 中支持 `${ADMIN_PASSWORD}` 和 `${JWT_SECRET}` 占位符，Spring Boot 原生支持环境变量替换
- **原因**: 零代码变更，Spring Boot 内置特性。生产部署时通过系统环境变量或 `.env` 文件注入

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              Post-Auth Platform Architecture                 │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────┐  ┌─────────────────────────┐  │
│  │     Auth Hardening       │  │    Phase 3 Extensions    │  │
│  │                          │  │                         │  │
│  │  ApiKeyCache (Caffeine)  │  │  GraphQL (schema-first) │  │
│  │  JwtBlacklist (DB table) │  │  Caffeine Cache Layer   │  │
│  │  RateLimit Filter        │  │  OWL Generator          │  │
│  │  Auth Audit (audit_logs) │  │  Federated Query Router  │  │
│  │  Env var secrets         │  │  Graph API (ontology)    │  │
│  └──────────────────────────┘  └─────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Shared Infrastructure                    │   │
│  │  ┌──────────┐  ┌───────────┐  ┌──────────────────┐  │   │
│  │  │ Ontop 5.5│  │  RDF4J    │  │  Spring Security  │  │   │
│  │  │ Engine   │  │  Rio/SAIL │  │  @PreAuthorize    │  │   │
│  │  └──────────┘  └───────────┘  └──────────────────┘  │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## Risks / Trade-offs

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| JWT 黑名单表增长 | 查询性能下降 | 定时清理过期条目（`@Scheduled`），`jti` 索引 |
| Rate limiting 误伤 | 合法请求被拒绝 | 可配置阈值，返回 `Retry-After` header |
| OWL 自动生成不准确 | 生成的 OWL 不符合预期 | 作为起稿工具而非最终结果，人工审查 |
| GraphQL N+1 查询 | 频繁 DB 查询 | 在 GraphQL DataFetcher 层批量查询 |
| 联邦查询性能 | 跨 DataSource 查询慢 | 限制并发、配置超时 |
| Caffeine 内存占用 | 缓存过多导致 OOM | 设置最大条目数 + weight-based eviction |
