## Why

当前平台不支持在线编辑 OWL/OBDA 文件。用户添加租户并配置 JDBC 后，只能手动在文件系统上编辑 OWL/OBDA 文件。Mapping Assistant 虽然能生成草稿，但不提供保存和应用功能。需要一套完整的在线编辑流程：生成 → 编辑 → 保存 → 生效。

## What Changes

- `tenants` 表新增 `owl_content` / `obda_content` 两列（或独立表），用于存储用户编辑的 OWL/OBDA 内容
- 新增 API 端点：生成 OWL/OBDA（从 DB 元数据）、保存内容、应用内容（重建引擎）
- `OntopEngine` 初始化逻辑改造：优先使用 DB 中保存的内容（写入 temp 文件），回退到文件路径
- Tenant 详情页新增 OWL/OBDA 编辑器面板（textarea），包含"From DB Metadata"、"Save Draft"、"Save & Apply"三个按钮
- Admin 页面 Tenant 列表增加"Edit Content"快速入口

## Capabilities

### New Capabilities
- `online-owl-obda-editor`: 在线编辑 OWL/OBDA 内容，支持从数据库元数据生成、手动修改、保存到数据库、应用到引擎

### Modified Capabilities

<!-- None -->

## Impact

- **后端**: `Tenant.java` 加字段，`TenantPersistenceService` SQL 加列，`OntopEngine.initialize()` 改造，`AdminController` 新增 3 个端点
- **前端**: `tenant/index.html` 新增编辑器卡片，`admin/index.html` 新增入口按钮
- **数据库**: 新增 `tenant_content` 表或 tenants 表加列
- **无需新增外部依赖**
