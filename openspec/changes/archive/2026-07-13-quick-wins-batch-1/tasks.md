## 1. 缓存逐出逻辑

- [x] 1.1 在 `CachedSparqlService` 中实现 `evictForTenant(String tenantId)` 方法，通过缓存键前缀模式逐出指定租户的所有缓存条目
- [x] 1.2 移除以 `// TODO: implement cache eviction logic` 注释
- [x] 1.3 更新测试验证逐出逻辑（`QueryCacheTest.evictForTenant` 已存在，更新构造函数注入）

## 2. 密钥严格模式

- [x] 2.1 在 `application.yml` 中新增 `ontology.auth.strict-mode` 配置属性（默认 `false`）
- [x] 2.2 创建 `SecretsValidator.java`，在 `@PostConstruct` 阶段检测所有默认密钥
- [x] 2.3 当 `strict-mode=true` 且检测到默认密钥时，抛出 `OntologyPlatformException` 阻止启动
- [x] 2.4 当 `strict-mode=false`（默认）时，仅记录警告日志（现有日志逻辑，现改用 `SecretsValidator`）

## 3. SPARQL 结果上限

- [x] 3.1 在 `application.yml` 中新增 `ontology.sparql.max-results` 配置属性（默认 10000）
- [x] 3.2 在 `CachedSparqlService` 中添加 `applyMaxResults()` 方法，解析 SPARQL 查询，检测是否已有 `LIMIT` 子句
- [x] 3.3 对没有 `LIMIT` 子句的 SELECT 查询自动追加 `LIMIT <max-results>`
- [x] 3.4 `max-results=0` 时不追加任何限制
- [x] 3.5 添加测试（`SparqlResultLimitTest`）：默认追加、已有 LIMIT 不修改、非 SELECT 不受影响

## 4. 仪表盘手动验证

- [x] 4.1 启动服务，`/ontology-viz/index.html` 返回 200 OK，页面包含 vis-network 脚本
- [x] 4.2 验证租户选择器列出 sample / university 两个租户（均已 health=UP）
- [x] 4.3 验证节点搜索功能（前端纯 JS 搜索，依赖 graph API 正常）
- [x] 4.4 验证交互操作（graph API 返回有效数据，SPARQL 查询成功）
- [x] 4.5 记录发现的问题并修复
  - **P0:** 已修复 — tenant JDBC URL 从 `jdbc:h2:mem:testdb` 改为 `jdbc:h2:mem:books`，两个租户引擎均初始化成功
  - **Info:** 使用 `exec:java` 而非 `spring-boot:run`（pom.xml 中 `<skip>true</skip>`）

## 5. OTel 分布式追踪端点验证

- [ ] 5.1 需要安装 Docker 启动 Jaeger（`docker run -p 16686:16686 -p 4318:4318 jaegertracing/all-in-one:latest`）— **当前环境无 Docker**
- [ ] 5.2 以 OTel Java Agent 启动应用 — **依赖 5.1**
- [ ] 5.3 发送 SPARQL/NLQ 请求后验证 Jaeger UI — **依赖 5.1**
- [x] 5.4 记录发现的问题
  - 无须 Agent 即可看到 OTLP 连接日志：`Failed to connect to localhost/[0:0:0:0:0:0:0:1]:4318` — 说明 Micrometer Tracing 桥接 OTLP exporter 已生效，仅缺 Jaeger 后端
