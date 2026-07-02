## Why

当前 API 认证仅支持 `X-API-Key` 静态列表，密钥明文存储、无权限分级、无登录 UI、无法为动态租户管理 API 提供用户级隔离。生产部署前需要补全认证鉴权体系。

## What Changes

- **管理后台登录**: 为 `/admin/**` 端点添加用户名密码登录，支持 Session 或 JWT
- **API Key 持久化**: 将 API Key 从 `application.yml` 静态配置迁移到数据库 `api_keys` 表，支持运行时 CRUD
- **权限分级**: 引入角色体系（`ROLE_ADMIN` / `ROLE_DEV` / `ROLE_READONLY`），控制不同端点的访问范围
- **敏感端点保护**: `reinit`、`audit-log/clear`、租户管理 API 需要 `ROLE_ADMIN`
- **Swagger UI 不降级**: 文档与健康检查保持公开

## Capabilities

### New Capabilities
- `admin-login`: 管理后台用户名密码登录认证（Session 或 JWT）
- `api-key-persistence`: API Key 数据库持久化与运行时管理
- `role-based-access`: 基于角色的 API 权限分级

### Modified Capabilities
*(none)*

## Impact

- `SecurityConfig.java` — 新增登录端点、JWT/会话配置、角色鉴权规则
- `ApiKeyFilter.java` — 从数据库动态加载有效密钥，不再依赖静态列表
- 新增 `LoginController`、`AdminAuthController` 等
- 新增 `api_keys` 表（DDL + entity + repository）
- `AuthIntegrationTest` 扩充覆盖角色与登录场景
- `application.yml` 中 `ontology.auth.api-keys` 变为可选（备用于启动初始 seed）
