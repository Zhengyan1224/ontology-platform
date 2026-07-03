## Context

项目已有 Spring Boot Actuator + Micrometer + Prometheus 指标体系（`MetricsService.java`、`CacheMetricsConfig.java`）。缺少请求级追踪。Spring Boot 3.4.x 内置 Micrometer Tracing 桥接，可与 OpenTelemetry 无缝集成。

## Goals / Non-Goals

**Goals:**
- 请求级端到端追踪（从 HTTP 入站 → SPARQL/NLQ → 引擎 → SQL）
- OTLP 导出到 Jaeger / Tempo 等后端
- 自定义业务跨度：SPARQL 执行、NLQ 处理、联邦查询子查询、缓存操作
- Agent 自动埋点覆盖 HTTP 和 JDBC

**Non-Goals:**
- 不替换现有 Micrometer + Prometheus 指标
- 不做 Metrics 到 OTel 的迁移
- 不引入 OTel Collector 部署（假设已有 Collector 端点）

## Decisions

- **D1: 混合方案 (Agent + Micrometer Tracing)** — Agent 做自动埋点（HTTP、JDBC），Micrometer Tracing 加自定义业务跨度。双通道互补。
- **D2: OTLP 导出** — 标准协议，兼容 Jaeger、Tempo、Zipkin 等后端。
- **D3: `@Observed` 注解** — Spring AOP 方式声明自定义跨度，无需侵入式 try-with-resources。

## Risks / Trade-offs

- [Risk] Agent 版本与 JDK 26 兼容性 → Mitigation: 使用最新的 OpenTelemetry Java Agent，JDK 26 尚在 EA 阶段时保留 Agent 选项
- [Risk] Agent 对启动时间的影响 → Mitigation: 仅在非开发环境启用 Agent
- [Risk] 跨度数据量 → Mitigation: OTel 采样率配置（`otel.traces.sampler`），默认 10%
