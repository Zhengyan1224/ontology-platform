## Why

完成五个快速见效的低风险收尾项，清理技术债务并为后续生产化奠定基础。这些项改动量小、风险低，但能显著提升平台的可观测性、安全性、可用性和代码整洁度。

## What Changes

1. **仪表盘手动验证** — 启动服务，逐项验证 `ontology-viz/index.html` 的页面渲染、租户切换、节点搜索和交互操作
2. **OTel 分布式追踪端点验证** — 启动 Jaeger all-in-one 容器，验证 `@Observed` 注解产生的 span 能到达 OTLP 端点
3. **缓存逐出逻辑** — 移除 `CachedSparqlService` 中的 TODO，添加显式的按租户缓存逐出方法
4. **密码与密钥管理模式** — 增加严格模式：检测到默认密钥时阻止启动（而非仅警告）
5. **SPARQL 结果集分页** — 添加可配置的 `max-results` 硬上限，保护服务端不被大结果集压垮

## Capabilities

### New Capabilities
- `sparql-pagination`: 可配置的 SPARQL 查询结果上限保护
- `secrets-strict-mode`: 启动时检测默认密钥并阻止启动

### Modified Capabilities
- 无现有 spec 变更

## Impact

- `CachedSparqlService.java` — 添加按租户缓存逐出方法
- `application.yml` — 新增 `ontology.auth.strict-mode`、`ontology.sparql.max-results` 配置
- `OntologyInitializer.java` — 或新的 `SecretsValidator` 启动检查
- 无 API 变更，无依赖变更
