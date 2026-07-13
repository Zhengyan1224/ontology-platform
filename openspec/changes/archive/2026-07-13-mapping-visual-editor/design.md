## Context

MappingAssistant 当前的工作流：JDBC metadata → 规则生成 OWL/OBDA → LLM 评审文本 → 给人看 → 结束。评审结果不写回配置，用户也无法直观编辑映射。

现有的前端只有只读的 vis-network 图 (`ontology-viz/index.html`)，后端有完整的规则引擎 (`OwlGeneratorService`, `ObdaGeneratorService`) 和可配置属性 (`OwlGenerationProperties`)。

## Goals / Non-Goals

**Goals:**
- 后端：Draft 响应增加 `editableConfig` 结构化字段，供前端渲染编辑表单
- 后端：新增 `PUT /tenants/{id}/mapping-assistant/config` 端点，接收前端编辑后的配置，写回配置并重新生成 OWL/OBDA
- 后端：LLM 评审改为同时提取结构化命名建议（JSON），用户可一键采纳
- 前端：在现有 `ontology-viz/index.html` 基础上扩展，左侧 Schema 树（可编辑），右侧 vis-network 图，底部 LLM 建议面板
- 零构建前端：纯静态 HTML + CDN vis-network，不引入 npm

**Non-Goals:**
- 多轮对话式配置（后续阶段再做）
- 直接编辑 OWL/OBDA 文件内容（只操作配置层，不操作文件层）
- 非浏览器客户端（移动端、CLI）

## Decisions

### D1: 编辑配置模型 — `EditableTableConfig` 结构

每次 Draft 响应返回一个扁平的可编辑配置列表，前端据此渲染编辑表单。

```json
{
  "editableConfig": {
    "tables": [
      {
        "tableName": "tb_books",
        "className": "Book",
        "classNameSuggested": "Book",
        "iriTemplate": "/{pk}",
        "expose": true,
        "columns": [
          {
            "columnName": "bk_code",
            "propertyName": "bkCode",
            "propertyNameSuggested": "code",
            "isPk": true,
            "isFk": false,
            "expose": true
          },
          {
            "columnName": "bk_title",
            "propertyName": "bkTitle",
            "propertyNameSuggested": "title",
            "isPk": false,
            "isFk": false,
            "expose": true
          }
        ]
      }
    ],
    "relationships": [
      {
        "fkTable": "tb_authors",
        "fkColumn": "wr_id",
        "pkTable": "tb_affiliated_writers",
        "objectPropertyName": "writtenBy",
        "objectPropertyNameSuggested": "writtenBy",
        "expose": true
      }
    ]
  }
}
```

- **Why**: 把 LLM 评审结果中的命名建议和用户当前配置放在一起，前端可以直接展示"当前值"和"建议值"的对比
- **Why not 直接发 OWL/OBDA 原文**: 用户需要编辑的是语义层配置（类名、属性名、暴露开关），不是文件格式；结构化数据让前端渲染更简单、后端回写更精确

### D2: 配置回写模式 — `PUT` 端点接收增量变更

```http
PUT /tenants/{id}/mapping-assistant/config
Content-Type: application/json

{
  "tables": [
    { "tableName": "tb_books", "className": "Book", "expose": true,
      "columns": [
        { "columnName": "bk_title", "propertyName": "title", "expose": true },
        { "columnName": "bk_code", "propertyName": "code", "expose": true }
      ]
    }
  ],
  "relationships": [
    { "fkTable": "tb_authors", "fkColumn": "wr_id", "objectPropertyName": "writtenBy", "expose": true }
  ]
}
```

后端处理：
1. 收到的配置合并到 `OwlGenerationProperties`（用 Map 存储覆盖规则）
2. 调用 `OwlGeneratorService.generateOwl()` + `ObdaGeneratorService.generateObda()` 重新生成
3. 返回新的 Draft 响应（含新的 `editableConfig`）

- **Why**: 增量变更而不是全量替换 —— 用户可能只改了 1 个字段名
- **Why not 写回配置文件**: 当前配置是 `application.yml` + `@ConfigurationProperties`，不应该运行时修改文件。使用内存 Map 覆盖，重启后恢复默认

### D3: 前端架构 — 零构建三层布局

在现有 `ontology-viz/index.html` 基础上拆为三个文件：

| 文件 | 职责 |
|------|------|
| `index.html` | 框架布局 + header + 页面串联 |
| `editor.js` | Schema 树渲染、编辑表单、LLM 建议面板、网络请求 |
| `editor.css` | 编辑 UI 样式（左侧树、面板、按钮等） |

前端交互流程：
```
用户操作          →  前端动作               →  后端 API
────────────────────────────────────────────────────────
加载页面          →  GET /tenants            ←  租户列表
选择租户          →  POST draft              ←  OWL/OBDA + editableConfig
编辑字段/改名     →  本地状态更新             ←  （无 API 调用）
点击"[采纳]"     →  采纳 LLM 建议值          ←  （本地替换建议值）
点击"应用"       →  PUT config              ←  更新配置 + 重新生成
→  收到新 Draft  →  刷新 Schema 树 + 图     ←  （前端重新渲染）
```

- **Why 零构建**: 现有代码库无 npm/build 工具链，引入会大大增加维护成本。纯 DOM 操作对于这种表单式编辑足够
- **Why 拆三个文件**: `index.html` 已有 264 行，再加编辑逻辑会难以维护。JS/CSS 分离后 `index.html` 只保留图渲染逻辑

### D4: LLM 结构化输出

修改 `MappingAssistantService` 的 LLM prompt，要求模型返回两部分：
1. **评审 Markdown**（给用户看，现有逻辑不变）
2. **结构化建议 JSON**（放在 markdown 的 ````json` 代码块中，后端解析）

```json
{
  "suggestions": {
    "tb_books": { "className": "Book", "columns": { "bk_title": { "propertyName": "title" } } },
    "tb_authors": { "className": "Author" }
  },
  "relationships": [
    { "fkTable": "tb_authors", "fkColumn": "wr_id", "objectPropertyName": "writtenBy" }
  ],
  "hideColumns": ["tb_books.api_token"]
}
```

- **Why**: 不改动现有评审流程，只在 LLM 回复末尾多解析一个 JSON 块。解析失败不影响评审文本
- **Why JSON in markdown 代码块**: LLM 输出 JSON 的可靠性高于纯文本提取

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|----------|
| LLM 输出的 JSON 格式错误 | 解析失败时静默忽略，回退到纯文本评审模式 |
| 运行时 Map 覆盖配置在重启后丢失 | 在 UI 中明确标注"当前为运行时配置，重启后恢复默认"，后续可加持久化 |
| `editableConfig` 在表多时体积大 | `editableConfig` 默认只包含 expose=true 的表和字段，用户可展开查看隐藏项 |
| 前端代码膨胀后难以维护 | 拆为独立 JS/CSS 文件；如果后续需求继续膨胀，再考虑引入轻量框架（Preact/Svelte） |
