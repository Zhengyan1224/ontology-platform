## 1. 数据库持久化 (api-key-persistence)

- [ ] 1.1 创建 `init-api-keys.sql` DDL（`api_keys` 表）
- [ ] 1.2 创建 `ApiKeyEntity`、`ApiKeyRepository`（Spring Data JPA）
- [ ] 1.3 实现 `ApiKeyService`（CRUD + SHA-256 哈希 + 缓存）
- [ ] 1.4 实现启动 seed：首次启动从 `application.yml` 导入静态 key
- [ ] 1.5 实现 `POST /api/v1/admin/api-keys`、`GET /api/v1/admin/api-keys`、`DELETE /api/v1/admin/api-keys/{id}` 端点

## 2. JWT 登录 (admin-login)

- [ ] 2.1 添加 jjwt 依赖到 `pom.xml`
- [ ] 2.2 实现 `JwtService`（签发、验证、解析 role）
- [ ] 2.3 实现 `POST /api/v1/auth/login` 端点（`LoginController`）
- [ ] 2.4 实现 `JwtAuthFilter`（从 `Authorization: Bearer` 头解析 JWT）
- [ ] 2.5 添加 `ontology.auth.jwt-secret` 和 `ontology.auth.jwt-expiration` 配置

## 3. 角色鉴权 (role-based-access)

- [ ] 3.1 重构 `SecurityConfig`：为每个端点组配置 `hasRole()` / `hasAnyRole()`
- [ ] 3.2 重构 `ApiKeyFilter`：从数据库验证 key + 读取 role 设置到 SecurityContext
- [ ] 3.3 添加 `@PreAuthorize` 注解到敏感 Controller 方法
- [ ] 3.4 在 `application.yml` 添加 `ontology.auth.admin-password` 配置

## 4. 测试与验证

- [ ] 4.1 更新 `AuthIntegrationTest`：覆盖 JWT 登录、角色隔离、DB key CRUD
- [ ] 4.2 运行全量测试确保无回归
- [ ] 4.3 更新 `docs/roadmap.md` 标记 2.1 为完成
