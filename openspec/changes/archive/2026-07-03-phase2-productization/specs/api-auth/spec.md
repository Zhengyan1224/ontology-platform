## ADDED Requirements

### Requirement: API Key 认证
系统 SHALL 支持通过 HTTP Header `X-API-Key` 传递 API Key 进行请求认证。
未提供有效 API Key 的请求 SHALL 返回 401 Unauthorized。
API Key SHALL 在 `application.yml` 中配置（`ontology.auth.api-keys`），支持多 Key。

#### Scenario: 有效 API Key 访问受保护端点
- **WHEN** 请求携带 `X-API-Key` header，值为配置中有效的 API Key
- **THEN** 请求正常处理，返回 200

#### Scenario: 无效 API Key
- **WHEN** 请求携带 `X-API-Key` header，值为未配置的 Key
- **THEN** 返回 401 Unauthorized

#### Scenario: 无 API Key
- **WHEN** 请求未携带 `X-API-Key` header
- **THEN** 返回 401 Unauthorized

### Requirement: 端点权限分级
系统 SHALL 将 API 端点分为公开和受保护两级：
- 公开端点：`/api/v1/health`, `/swagger-ui.html`, `/h2-console/**`, `/v3/api-docs/**`
- 受保护端点：所有其他 `/api/v1/**` 端点（SPARQL、NLQ、Admin 操作）

#### Scenario: 公开端点免认证
- **WHEN** 请求 `/api/v1/health` 不带 API Key
- **THEN** 正常返回 200

#### Scenario: 受保护端点需认证
- **WHEN** 请求 `/api/v1/tenants` 不带 API Key
- **THEN** 返回 401

### Requirement: 开发模式跳过认证
系统 SHALL 支持通过 `ontology.auth.enabled: false` 配置在开发环境完全跳过认证。
默认值 SHALL 为 `true`（认证启用）。

#### Scenario: 开发模式关闭认证
- **WHEN** `ontology.auth.enabled` 为 `false` 且请求不带 API Key
- **THEN** 所有端点正常处理

### Requirement: 可配置 API Key
系统 SHALL 支持在 `application.yml` 中配置多个 API Key：
```yaml
ontology:
  auth:
    enabled: true
    api-keys:
      - admin-key-001
      - dev-key-002
```

#### Scenario: 多个 API Key 均有效
- **WHEN** 请求使用 `api-keys` 列表中任意一个 Key
- **THEN** 请求通过认证
