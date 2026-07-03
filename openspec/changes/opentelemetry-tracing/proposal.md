## Why

项目已有 Micrometer + Prometheus 指标，但在分布式场景下缺乏请求级别的端到端追踪能力。OTel 追踪可以串联 SPARQL 查询 → SQL 重写 → 数据库执行 → 联邦子查询的完整调用链，帮助诊断性能瓶颈和错误。

## What Changes

- 添加 OpenTelemetry Java Agent 自动埋点（HTTP、JDBC、线程池）
- 添加 `micrometer-tracing-bridge-otel` 依赖，桥接 Micrometer 与 OTel
- 添加 `opentelemetry-exporter-otlp` 依赖，导出追踪到 OTLP Collector
- 在关键服务方法添加 `@Observed` / `Observation` 自定义跨度
- 配置 `application.yml` 中的 OTel 导出和目标地址

## Capabilities

### New Capabilities
- `opentelemetry-tracing`: 端到端分布式追踪，覆盖 SPARQL 查询、NLQ 处理、联邦查询等核心链路

### Modified Capabilities
<!-- No existing spec-level changes -->

## Impact

- 新增 Maven 依赖：`micrometer-tracing-bridge-otel`、`opentelemetry-exporter-otlp`
- 新增 JVM 启动参数：`-javaagent:path/to/opentelemetry-javaagent.jar`
- 新增配置：`management.tracing.*`、`otel.*`
- 修改服务类：`FederatedQueryService`、`CachedSparqlService`、`NaturalLanguageQueryService`、`SparqlController` 添加 `@Observed`
