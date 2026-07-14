## Context

当前 Tenant 的 OWL/OBDA 文件仅支持从文件系统读取（`src/main/resources/ontologies/`），无在线编辑能力。
Mapping Assistant 虽能生成草稿但只返回 JSON 展示，不提供持久化存储和引擎重载能力。

需要在现有架构上新增一套"生成 → 编辑 → 存储 → 应用"的闭环。

## Goals / Non-Goals

**Goals:**
- 用户可在 Web UI 上从 DB 元数据生成 OWL/OBDA
- 用户可在 textarea 中手动修改 OWL/OBDA 内容
- 用户可保存编辑内容到数据库
- 用户可一键"保存并生效"（重建 Ontop 引擎）
- 向后兼容：已有 tenant 不受影响，默认仍从文件路径加载

**Non-Goals:**
- 语法高亮和代码补全（后续再考虑）
- 多人协作编辑
- OWL/OBDA 版本历史
- 文件上传替代手动编辑

## Decisions

### 1. 存储方式：独立表 `tenant_content` vs tenants 加列

**选择：独立表 `tenant_content`**

理由：
- 不改变 tenants 表结构，不影响现有 CRUD
- 内容可能很大（CLOB），独立表更清晰
- 未来扩展版本历史更方便

```sql
CREATE TABLE IF NOT EXISTS tenant_content (
    tenant_id VARCHAR(100) PRIMARY KEY,
    owl_content CLOB,
    obda_content CLOB,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 2. 引擎初始化优先从 DB 内容加载

`OntopEngine.initialize()` 改造：

```
if (tenantContent 存在 owl_content) →
   写入 temp 文件 → .ontologyFile(tempOwlUri)
else → .ontologyFile(原文件路径)
```

Temp 文件用 `Files.createTempFile(prefix, suffix)`，应用退出时自动清理。

### 3. API 端点设计

在 `AdminController` 中新增（或新建 `ContentController`），统一前缀 `/api/v1/tenants/{tenantId}/`：

| 方法 | 路径 | 请求/响应 | 说明 |
|------|------|-----------|------|
| POST | `/generate` | → `{ owlContent, obdaContent }` | 从 DB 元数据生成，不保存 |
| PUT | `/content` | Body: `{ owlContent, obdaContent }` | 保存到 `tenant_content` 表 |
| POST | `/apply` | → `{ status, health }` | 保存 `tenant_content` + 重建引擎 + 清缓存 |

`POST /generate` 不需要 Admin 权限（因为是预览），`PUT /content` 和 `POST /apply` 需要 `ROLE_ADMIN`。

### 4. 前端改造

在现有 `tenant/index.html` 的 Edit 卡片下方新增 OWL/OBDA 编辑器卡片：

```
┌─ OWL / OBDA Editor ──────────────────────────────────┐
│                                                        │
│  ┌─ OWL (Turtle) ───────────────────────────────────┐ │
│  │  <textarea id="owl-editor" rows="25" ...>       │ │
│  └──────────────────────────────────────────────────┘ │
│                                                        │
│  ┌─ OBDA ───────────────────────────────────────────┐ │
│  │  <textarea id="obda-editor" rows="15" ...>       │ │
│  └──────────────────────────────────────────────────┘ │
│                                                        │
│  [Generate from DB] [Save Draft] [Save & Apply]        │
└────────────────────────────────────────────────────────┘
```

按钮逻辑：
- **Generate from DB**: `POST /generate` → 填充两个 textarea
- **Save Draft**: `PUT /content` → toast "Saved"
- **Save & Apply**: `POST /apply` → toast "Applied" → 刷新 tenant 状态

### 5. 权限

- `POST /generate`: 不需要 ROLE_ADMIN（任何 API Key 可预览）
- `PUT /content`: 需要 ROLE_ADMIN
- `POST /apply`: 需要 ROLE_ADMIN

## Risks / Trade-offs

- [Temp 文件残留] → `Files.createTempFile()` 由 JVM 在退出时清理，单次应用生命周期内最多同时存在少量 temp 文件，无实质风险
- [引擎重建期间查询失败] → `POST /apply` 是同步操作，客户端等待期间其他查询会收到错误。可在后续迭代中加健康检查重试逻辑
- [CLOB 大内容] → OWL/OBDA 文件通常 < 1MB，CLOB 足够。如果未来文件极大，可考虑流式读写
- [DB 内容与文件系统不一致] → 设计上 DB 内容优先。用户可通过 PUT /content 清空内容（设为 null）回退到文件路径模式
