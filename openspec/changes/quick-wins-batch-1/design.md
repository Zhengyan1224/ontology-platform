## Context

当前平台已完成核心功能和多项产品化扩展，155 个测试通过。本批次聚焦五项快速见效的低风险项：
1. **仪表盘验证** — `ontology-viz/index.html` 代码完成但未手动验证
2. **OTel 追踪验证** — `@Observed` 注解 + Micrometer Tracing 配置完成但未端到端验证
3. **缓存逐出** — `CachedSparqlService` 中有一行未完成的 TODO
4. **密钥严格模式** — 检测到默认密钥时仅警告，缺少阻止启动的严格模式
5. **SPARQL 结果上限** — 大结果集查询可能撑爆内存，缺少默认上限保护

## Goals / Non-Goals

**Goals:**
- 完成仪表盘和 OTel 追踪的手动端到端验证，发现并修复问题
- 移除 `CachedSparqlService` TODO，实现显式的按租户缓存逐出
- 增加 `strict-mode` 配置，默认密钥时阻止启动
- 增加可配置的 SPARQL 结果集最大行数上限

**Non-Goals:**
- 不涉及新功能开发（验证本身就是目标）
- 不修改现有 API 签名或返回格式
- 不引入新的外部依赖

## Decisions

### D1: 缓存逐出策略 — 使用显式逐出方法而非 TODO
- **方案**：在 `CachedSparqlService` 中实现 `evictByTenant(String tenantId)` 方法，调用 `CacheManager.getCache("sparqlQueries").evictIf(tenantId)` 或遍历清空
- **替代方案**：给每个缓存 key 加 tenant 前缀并按前缀批量逐出
- **选择理由**：前缀方式代码侵入更小，不影响现有缓存逻辑

### D2: 密钥严格模式 — 启动时检查而非运行时检查
- **方案**：`SecretsValidator` 在 `@PostConstruct` 阶段检查所有密钥配置，若检测到仍在用默认值且 `ontology.auth.strict-mode=true`，则抛出异常阻止启动
- **替代方案**：在 API 请求时延迟检查（不安全），或在 Filter 中拒绝请求（影响性能）
- **选择理由**：尽早失败（fail-fast）原则，避免启动后才发现密钥问题

### D3: SPARQL 结果上限 — 查询解析时注入 LIMIT
- **方案**：在 `CachedSparqlService.executeQuery()` 中解析 SPARQL 查询 AST，若没有 LIMIT 子句且 `max-results` 配置 > 0，自动追加 `LIMIT <max-results>`
- **替代方案**：结果集返回时截断（浪费计算资源），或在查询执行后计数并抛异常
- **选择理由**：在 SQL 层面就限制行数，避免 Ontop 重写后产生大结果集浪费计算

### D4: 验证项记录 — 使用 checklist 引导而非自动化测试
- **方案**：创建手动验证清单文档，按步骤记录验证结果和发现的问题
- **理由**：这两项验证涉及外部组件（vis-network CDN、Jaeger），无法纯单元测试覆盖

## Risks / Trade-offs

- **[风险] 自动追加 LIMIT 可能改变语义**：严格来说，不加 LIMIT 的 SPARQL 期望返回所有结果。追加 LIMIT 在改变行为。→ **缓解**：设置合理的默认值（如 10000），允许配置为 0 表示无限制，并在文档中说明
- **[风险] Jaeger 版本兼容性**：all-in-one 镜像版本可能与 OTel exporter 协议版本不匹配 → **缓解**：使用与 OTel SDK 版本匹配的 Jaeger 版本
- **[风险] 严格模式可能阻塞启动**：如果 CI 环境也使用了默认密钥 → **缓解**：strict-mode 默认 false，仅生产环境显式开启
