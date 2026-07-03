## Context

当前 graph 端点 (`GET /api/v1/tenants/{tenantId}/graph`) 返回 JSON 格式的 `{nodes, edges}` 本体图数据。项目为纯后端，无前端资源。需要一个轻量内嵌页面来消费此数据并可视化展示。

## Goals / Non-Goals

**Goals:**
- 单页静态 HTML 应用，嵌入 Spring Boot `static/` 目录
- 使用 vis-network CDN 渲染力导向图（拖拽、缩放、高亮）
- 租户选择器下拉框，切换不同租户的本体视图
- 节点搜索/过滤功能
- 无需 npm / webpack / 新 Maven 依赖

**Non-Goals:**
- 非 SPA 框架（React/Vue/Angular）
- 非服务器端渲染（Thymeleaf）
- 不修改后端 graph 端点格式
- 不做认证（复用已有 SecurityConfig，可匿名访问页面）

## Decisions

- **D1: vis-network over D3.js** — vis-network API 更简洁，内置力导向布局、拖拽、缩放、点击高亮，几分钟即可集成。D3.js 需更多自定义代码。
- **D2: CDN over npm** — 零构建工具，零依赖管理。Spring Boot 单 jar 部署时无需额外处理。
- **D3: 单 HTML 文件 + 内联 CSS/JS** — 避免多文件引用问题，部署简单。
- **D4: 匿名访问页面** — 页面本身无敏感数据，复用 `/api/v1/tenants/{tenantId}/graph` 端点的认证机制（该端点受 Spring Security 保护）。

## Risks / Trade-offs

- [Risk] CDN 可用性 — vis-network 需从 CDN 加载，断网时无法工作 → Mitigation: 页面加载时 fallback 提示
- [Risk] 大本体性能 — 如果节点数 > 500，vis-network 渲染可能卡顿 → Mitigation: 在 JS 中做节点数告警，并启用物理引擎调优选项
