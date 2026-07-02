## Why

NLQ（自然语言查询）是平台的核心差异化能力，但目前模板和 prompt 全部硬编码在 Java 中，导致每新增租户需改代码、LLM prompt 缺少示例引导、响应阻塞无流式体验、无多轮对话能力。提升 NLQ 的灵活性与用户体验，使平台更贴近生产使用。

## What Changes

- **数据驱动模板**: 将 `SparqlTemplateGenerator` 中的 11 个硬编码 regex 模板抽取为外部 YAML 配置，每个租户可自定义模板文件
- **LLM prompt 优化**: 引入 few-shot 示例、schema 结构化描述、输出格式约束，提高 LLM 生成 SPARQL 的成功率
- **流式响应**: NLQ 查询结果通过 SSE (Server-Sent Events) 逐步推送给前端（中断长等待、实时展示 SPARQL 与中间结果）
- **多轮对话**: 在服务端维护会话上下文，支持追问（如 "列出教授" → "他们中谁在 CS 系？"）

## Capabilities

### New Capabilities
- `template-driven-nlq`: 外部化 NLQ 模板为 YAML 配置，支持每租户自定义多组 question → SPARQL 规则
- `llm-prompt-enhancement`: 优化 LLM prompt 结构，包含 few-shot 示例、schema 格式约束
- `streaming-nlq`: NLQ 后端通过 SSE 流式返回结果，前端逐步展示
- `conversational-nlq`: 服务端会话管理，支持基于上下文的追问

### Modified Capabilities
*(none)*

## Impact

- `NaturalLanguageQueryService.java` — 重构 LLM 调用链、集成流式 SSE 输出
- `SparqlTemplateGenerator.java` — 从 Java 硬编码改为加载 YAML 配置
- `NlqController.java` — 新增 SSE 端点、会话 ID 参数
- 新增 `src/main/resources/nlq-templates/` 目录存放 YAML 模板文件
- 新增 `ChatSessionService` 或类似组件管理多轮对话上下文
- 测试文件相应更新
