## Why

本体可视化 API（Phase 3.6）已有 graph 端点返回 `{nodes, edges}` 格式数据，但缺乏可视化的前端界面。添加一个轻量内嵌页，使开发者和用户可以直接在浏览器中浏览、探索本体结构（类层次、属性关系），无需外部工具。

## What Changes

- 在 `src/main/resources/static/` 下创建静态 HTML/CSS/JS 单页应用
- 页面调用 `GET /api/v1/tenants/{tenantId}/graph` 获取本体图数据
- 使用 vis-network（CDN）渲染力导向图，支持拖拽、缩放、节点聚焦
- 添加租户选择器，可在不同租户的本体间切换
- 添加节点搜索栏，快速定位类或属性
- 页面无需认证（允许公开访问），或复用已有认证机制
- Spring Boot 自动服务 `static/` 目录下的静态资源（无需新增配置）

## Capabilities

### New Capabilities
- `ontology-visualization-dashboard`: 基于 graph 端点的浏览器端本体可视化界面，使用 vis-network CDN 渲染力导向图

### Modified Capabilities
<!-- No existing spec-level changes -->

## Impact

- 新增 `src/main/resources/static/` 目录及 HTML/CSS/JS 文件
- `SecurityConfig.java` 可能需要放开 `/ontology-viz/**` 或 `/index.html` 的匿名访问
- 无新 Maven 依赖（vis-network 通过 CDN 加载）
- 无后端代码变更（复用现有 graph 端点）
