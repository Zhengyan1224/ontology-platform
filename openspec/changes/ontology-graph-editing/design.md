## Context

当前 `/ontology-viz/` 页面使用 vis-network 渲染本体图，但只读。用户交互限于浏览、搜索、重命名节点。没有拖拽加边、右键菜单或自定义 OWL 公理的能力。

后端已有 `GET /api/v1/tenants/{tenantId}/graph` 端点，返回 `{nodes, edges}` 格式数据，由 `OntologyGraphService` 从 OWL 文件解析生成并缓存。OWL/OBDA 内容通过 `MappingAssistantService.applyConfig()` 应用 `EditableConfig`（类名/属性名覆盖）后重新生成。

需要在现有可视化框架上叠加编辑能力，核心新增一个公理层（AxiomConfig）存储用户通过图编辑产生的 OWL 公理，与 DB 派生内容合并输出。

## Goals / Non-Goals

**Goals:**
- 在 `/ontology-viz/` 页面增加 View/Edit 模式切换
- Edit 模式下从节点拖拽创建 subClassOf 边
- 右键菜单：节点重命名、添加子类、删除类、删除边
- 用户添加的公理存储在 `tenant_content.axiom_config` JSON 列
- Apply 时 DB 派生 OWL + AxiomConfig 合并输出完整 Turtle
- 图布局（节点位置）Apply 后持久化，下次加载还原
- Generate from DB 清除 AxiomConfig 和所有用户覆盖

**Non-Goals:**
- 非 subClassOf 的公理编辑（equivalentClass, disjointWith 等留 Phase 2）
- 并发编辑冲突解决（以最后保存为准）
- 图编辑的撤销/重做
- 编辑历史记录

## Decisions

### D1: AxiomConfig 存储格式

`tenant_content` 表新增 `axiom_config` 列，类型为 JSON 文本。

```json
{
  "subClassOf": [
    { "child": "Magazine", "parent": "Publication", "id": "uuid-1" }
  ],
  "layout": {
    "Magazine": { "x": 100, "y": 200 },
    "Publication": { "x": 100, "y": 100 }
  }
}
```

每条文理有 `id`（UUID），前端生成，用于精确定位删除/编辑。`layout` 字段存节点位置。

**替代方案考虑：**
- 独立 axiom 表 → 更规范化但增加了查询复杂度，一期不需要
- 直接在 Turtle 里追加 → 解析成本高，难做结构化编辑
- 结论：JSON 列足够，与 `owl_content` / `obda_content` 共存于同一行

### D2: 合并策略 — DB 派生 + 用户公理

OWL 生成流程：

```
OwlGeneratorService.generateOwl() → Turtle A (DB派生)
     +
AxiomConfig.subClassOf → Turtle B (用户公理)
     +
命名覆盖 (tableOverrides / columnOverrides)
     ↓
最终 Turtle = A + B
```

两者不冲突：用户加的 `subClassOf` 是额外的三元组，不覆盖 DB 生成的任何内容。DB FKs 产生的 ObjectProperty 与用户加的同名 subClassOf 并存。

### D3: View/Edit 模式

- **View 模式**：当前行为，图只读，可拖动位置
- **Edit 模式**：开启 vis-network `manipulation`、显示节点手柄、启用右键菜单
- 用户添加的边在 Apply 前以虚线显示（Edit 模式可见）
- 切换到 View 模式时隐藏虚线边
- 切换不丢编辑状态（保留在 AxiomConfig 内存副本中）

### D4: 布局持久化

- 拖拽节点位置 Apply 时写入 `axiom_config.layout`
- 加载图时读取 layout，用 `vis.Network.moveNode()` 或 `options.physics` 关闭后设置位置
- Generate from DB 时清除 layout，重新让物理引擎布局

### D5: 生成时清除逻辑

```
Generate from DB 点击时:
  1. 清除 namingProperties 中的所有 tableOverrides / columnOverrides
  2. 清除 axiom_config → {}
  3. 重新读取 DB metadata
  4. 生成新的 editableConfig
  5. 重新渲染图（无物理引擎预置）
```

### D6: 图缓存失效

Apply 成功后，需要清除 `OntologyGraphService` 中对应租户的图缓存，下次请求重新生成。

## Risks / Trade-offs

- [Risk] 大图性能：节点 > 500 时拖拽加边可能卡顿 → Mitigation: 加载时提示节点数，Edit 模式下建议聚焦子图
- [Risk] layout 坐标在不同屏幕尺寸下表现不一致 → Mitigation: layout 存相对位置（百分比），或使用 vis-network 的 fit() 自适应
- [Risk] axiom_config JSON 无 schema 校验 → Mitigation: 后端写入前做基本校验（必填字段、类型检查）
- [Trade-off] JSON 列存 axiom 不可单独 SQL 查询 → 当前无按公理查询的需求，可接受
