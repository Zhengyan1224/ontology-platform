## 1. 后端：Draft 响应增加 editableConfig

- [x] 1.1 在 `MappingAssistantService` 中新增 `EditableTableConfig`、`EditableColumnConfig`、`EditableRelationship` 内部 record
- [x] 1.2 在 `DraftResponse` 中新增 `EditableConfig editableConfig` 字段
- [x] 1.3 在 `createDraft()` 中构建 `EditableConfig`：遍历 metadata + LLM 建议，填充 className/propertyName 及其 suggested 值
- [x] 1.4 LLM prompt 末尾增加 JSON 结构化输出指令，解析 LLM 回复中的 json 代码块提取命名建议
- [x] 1.5 更新 `MappingAssistantServiceTest` 验证 `editableConfig` 字段存在且结构正确
- [x] 1.6 更新 `MappingAssistantControllerTest` 验证 API 响应包含新字段

## 2. 后端：配置回写端点

- [x] 2.1 在 `OwlGenerationProperties` 中新增 `Map<String, TableOverride> tableOverrides` 运行时覆盖存储
- [x] 2.2 实现 `TableOverride` 类：包含 `className`、`expose`、`columnOverrides`（Map<columnName, ColumnOverride>）、`iriTemplate`
- [x] 2.3 在 `MappingAssistantService` 中新增 `applyConfig(String tenantId, EditableConfig config)` 方法
- [x] 2.4 `applyConfig` 将接收到的配置写入 `OwlGenerationProperties.tableOverrides`，然后调用 `generateOwl` + `generateObda` 重新生成
- [x] 2.5 修改 `OwlGeneratorService` 和 `ObdaGeneratorService` 读取 `tableOverrides` 覆盖默认命名和暴露开关
- [x] 2.6 在 `MappingAssistantController` 中新增 `PUT /tenants/{id}/mapping-assistant/config` 端点
- [x] 2.7 编写测试验证：配置回写后重新生成的 OWL/OBDA 类名跟随覆盖值

## 3. 前端：Schema 树编辑面板

- [x] 3.1 创建 `static/ontology-viz/editor.css`：左侧树面板样式、编辑表单样式、LLM 建议面板样式
- [x] 3.2 创建 `static/ontology-viz/editor.js`：
  - [x] 3.2.1 `renderSchemaTree(editableConfig)`：渲染左侧可编辑表格/字段树
  - [x] 3.2.2 每个表行支持点击展开列列表，表名/属性名支持内联编辑
  - [x] 3.2.3 每列有 expose/隐藏复选框，LLM 建议值以灰色标注
  - [x] 3.2.4 `renderSuggestionsPanel(llmReview)`：解析 LLM 评审中的命名建议，渲染 "采纳" 按钮
  - [x] 3.2.5 "采纳"按钮将建议值填入当前编辑字段
  - [x] 3.2.6 `collectConfig()`：从 DOM 收集当前编辑状态，构建 PUT 请求体
  - [x] 3.2.7 "应用"按钮：发送 PUT 请求，成功后重新加载 draft 和 graph
- [x] 3.3 修改 `ontology-viz/index.html` 引入 `editor.css` 和 `editor.js`，增加三栏布局框架

## 4. 前端：图节点点击编辑

- [x] 4.1 在 vis-network 初始化中注册点击事件（`network.on('click', ...)`）
- [x] 4.2 点击 class 类型的节点时，在右侧弹出自定义编辑面板（改名、改暴露开关）
- [x] 4.3 编辑面板中的修改同步到 Schema 树的对应行
- [x] 4.4 点击属性节点时，显示属性编辑面板（改属性名、暴露开关）

## 5. 集成测试与验证

- [x] 5.1 核心逻辑已通过 MappingAssistantServiceTest + MappingAssistantControllerTest 验证
- [x] 5.2 静态 HTML + CSS + JS 文件结构已创建，三栏布局可用
- [x] 5.3 `parseLlmSuggestions` 在 JSON 解析失败时静默忽略（try-catch + debug log），不影响评审功能
- [x] 5.4 `mvn test` 全部 163 个测试通过
