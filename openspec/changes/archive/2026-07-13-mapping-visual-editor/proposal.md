## Why

当前 MappingAssistant 只输出 LLM 评审文本，评审建议无法回写到配置，用户也无法直观地查看和编辑 OWL/OBDA 映射。需要构建一个可视化编辑界面，让用户能通过点击/拖拽/表单直接修改映射配置，并与 LLM 评审形成"建议→确认→重生成"的闭环。

## What Changes

- 新增 `POST /tenants/{id}/mapping-assistant/apply` 端点，接收用户编辑后的映射配置，写回 `OwlGenerationProperties` 并重新生成 OWL/OBDA
- 重构 `MappingAssistantService#createDraft` 响应，增加 `editableConfig` 结构化字段（表/字段/关系映射列表），供前端渲染编辑表单
- 在现有 `ontology-viz/index.html` 基础上扩展：左侧 Schema 树（可编辑命名/暴露开关），右侧 vis-network 图，底部 LLM 建议面板
- LLM 评审改为可提取结构化命名建议（JSON），用户可一键采纳/忽略
- 保持零构建前端（纯静态 HTML + vis-network CDN）

## Capabilities

### New Capabilities
- `mapping-visual-editor`: 可视化映射编辑能力，包括 Schema 树编辑、图联动、LLM 建议采纳、配置回写

### Modified Capabilities
- `sql-ddl-to-owl`: 修改 `POST /tenants/{id}/generate-mapping` 端点和 MappingAssistant 以支持配置回写和增量更新
- `ontology-visualization`: 在现有 vis-network 只读图基础上增加交互编辑面板

## Impact

- 后端：`MappingAssistantService`、`OwlGenerationProperties`、`MappingAssistantController` 改造
- 前端：`static/ontology-viz/index.html` 扩展，新增 `static/ontology-viz/editor.js`、`static/ontology-viz/editor.css`
- API：新增 `PUT /tenants/{id}/mapping-assistant/config` 端点，`POST draft` 响应增加 `editableConfig` 字段
- 无新增依赖，零构建
