## Why

当前本体可视化页面是只读的，用户不能在图界面中创建或修改关系。需要在可视化的基础上增加交互编辑能力，使用户可以直接在图上拖拽创建 subClassOf 关系、管理自定义公理，并通过 LLM 辅助生成本体结构。

## What Changes

- 在 `/ontology-viz/` 页面增加 View/Edit 模式切换
- Edit 模式下支持拖拽节点创建 subClassOf 边（带边类型选择弹窗）
- 右键菜单：节点重命名、添加子类、删除类；边删除
- 用户通过图编辑产生的公理存储在 `tenant_content` 表的 `axiom_config` JSON 列中
- Apply 时合并 DB 派生 OWL + 用户公理 → 生成完整 Turtle
- Generate from DB 时清除所有用户自定义公理
- 用户拖拽节点位置 Apply 后持久化，下次加载还原布局
- 编辑器后端新增 `axiom_config` 读写 API

## Capabilities

### New Capabilities
- `ontology-graph-editing`: 本体可视化图的交互编辑能力，支持 View/Edit 模式切换、拖拽创建 subClassOf 边、右键菜单、布局持久化

### Modified Capabilities
- `ontology-visualization`: 需要在现有 graph 端点返回的数据基础上，支持编辑模式下的 UI 交互（拖拽、右键、模式切换属于前端新增行为，数据格式不变）

## Impact

- `TenantContentRepository`：新增 `axiom_config` 列的读写
- `OwlGeneratorService`：读取 AxiomConfig 并合并输出 subClassOf 公理
- `editor.js` / `editor.css` / `index.html`：增加拖拽事件、右键菜单、mode toggle、布局存储
- `MappingAssistantService.EditableConfig`：可选新增 `axiomConfig` 字段
- 新增 API 端点：获取/保存 axiom_config
- 无新 Maven 依赖
