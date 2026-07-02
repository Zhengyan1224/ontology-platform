## Context

当前认证体系：`ApiKeyFilter` 从 `application.yml` 静态列表读取 `X-API-Key`，密钥明文、无角色、无登录。所有受保护端点一视同仁。Spring Security 6 已集成但仅用了最简配置。

## Goals / Non-Goals

**Goals:**
- API Key 持久化到 `api_keys` 表，支持运行时增删改查
- 角色体系：`ROLE_ADMIN`、`ROLE_DEV`、`ROLE_READONLY`
- 管理后台登录端点（`POST /api/v1/auth/login`），返回 JWT
- 敏感端点（reinit、audit-log/clear、租户 CRUD）要求 `ROLE_ADMIN`
- 查询端点（SPARQL、NLQ、schema）可配置为 `ROLE_DEV` 或 `ROLE_READONLY`
- 下放兼容：旧 X-API-Key 仍可工作（只要在 `api_keys` 表中）

**Non-Goals:**
- 不实现 OAuth2 / SSO 集成
- 不实现用户注册 UI（仅 API 级管理）
- 不实现 API 限流（rate limiting）

## Decisions

### D1 — JWT 而非 Session
选择 JWT（jjwt 库）而非 HttpSession，保持无状态架构（现有 stateless 配置不变）。
- JWT payload 嵌入 `sub`（API Key ID）、`role`、`tenant`（可选）
- JWT 过期时间可配置（默认 1 小时）

### D2 — API Key 数据库模型
```sql
CREATE TABLE api_keys (
    id        VARCHAR(36) PRIMARY KEY,
    key_hash  VARCHAR(64) NOT NULL,
    label     VARCHAR(100),          -- 备注名
    role      VARCHAR(20) NOT NULL DEFAULT 'ROLE_DEV',
    enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL
);
```
密钥用 SHA-256 哈希存储，原始密钥仅在创建时返回一次。

### D3 — 双重认证支持
保留 `X-API-Key` header 认证，同时新增 `Authorization: Bearer <jwt>` 认证。
`ApiKeyFilter` 改为读取 DB 验证密钥哈希 + 角色。新增 `JwtAuthFilter` 处理 Bearer token。
两个 filter 均用 `OncePerRequestFilter`，放在 `SecurityFilterChain` 中。

### D4 — 管理 API 与查询 API 隔离
- `/api/v1/tenants`（GET 列表）→ `ROLE_READONLY`
- `/api/v1/tenants/{id}`（POST/PUT/DELETE）→ `ROLE_ADMIN`
- `/api/v1/tenants/{id}/reinit` → `ROLE_ADMIN`
- `/api/v1/audit-log/clear` → `ROLE_ADMIN`
- `/api/v1/sparql/**`, `/api/v1/nlq/**` → `ROLE_DEV`
- `/api/v1/health`, `/swagger-ui/**`, `/v3/api-docs/**` → 公开

### D5 — 启动 seed
首次启动时（`api_keys` 表为空），自动从 `application.yml` 的 `ontology.auth.api-keys` 列表 seed 入数据库，标记为 `ROLE_ADMIN`，方便开发。

## Risks / Trade-offs

- **[JWT 无法撤销]** JWT 签发后到期前无法吊销 → 短期过期（默认 1h）+ 提供 `/auth/revoke` 黑名单接口（可选）
- **[SHA-256 哈希]** 不是 bcrypt，但用于 API Key 而非密码已足够 → 如需更高安全性可升级为 bcrypt
- **[向后兼容]** 现有测试使用 `X-API-Key: admin-key-001` 需确保 seed 逻辑覆盖 → seed 时包含这两个 key
