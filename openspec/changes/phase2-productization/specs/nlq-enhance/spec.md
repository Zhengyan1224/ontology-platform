## ADDED Requirements

### Requirement: 数据驱动模板
系统 SHALL 将 SPARQL 模板从 Java 硬编码改为 YAML 配置文件。
模板配置 SHALL 按租户组织，每个租户可自定义模板列表。
每个模板 SHALL 包含：`name`, `pattern` (regex), `sparql` (含 `{param}` 占位符), `description`。
模板配置文件路径 SHALL 为 `src/main/resources/nlq-templates/{tenantId}.yml`。
当租户没有对应模板文件时，SHALL 回退到默认模板（纯 LLM 模式）。

#### Scenario: 模板从 YAML 加载
- **WHEN** 系统启动时加载租户 `sample` 的 NLQ 请求
- **THEN** 从 `src/main/resources/nlq-templates/sample.yml` 加载模板

#### Scenario: 无模板文件时回退 LLM
- **WHEN** 请求的租户不存在模板配置 YAML
- **THEN** 直接使用 LLM 生成 SPARQL（如 LLM 不可用则返回错误）

#### Scenario: 模板参数替换
- **WHEN** 用户提问匹配模板 `who wrote {title}`
- **THEN** 系统将 `{title}` 替换为用户输入中提取的实际值，生成完整 SPARQL

### Requirement: LLM Prompt 优化
系统 SHALL 在 LLM prompt 中加入 few-shot 示例（2-3 个问答对），提升 SPARQL 生成质量。
Few-shot 示例 SHALL 从租户的模板配置中自动提取（模板同时作为示例来源）。
Prompt SHALL 包含：系统角色描述、Schema 上下文、Few-shot 示例、用户问题。

#### Scenario: Prompt 包含 few-shot 示例
- **WHEN** LLM 模式处理 NLQ 请求
- **THEN** 发送给 LLM 的 prompt 包含 2-3 个示例 SPARQL 查询

### Requirement: 流式 SSE 响应
系统 SHALL 支持通过 `Accept: text/event-stream` 头部触发 NLQ 流式响应。
流式响应 SHALL 使用 SSE (Server-Sent Events) 协议。
流式响应 SHALL 逐步输出：中间状态（`status: reasoning`、`status: generating`）→ 最终 SPARQL → 查询结果。
新增端点或通过现有 `POST /api/v1/tenants/{tenantId}/nlq` 的 `Accept` header 区分。

#### Scenario: SSE 流式 NLQ 请求
- **WHEN** 请求 NLQ 端点指定 `Accept: text/event-stream`
- **THEN** 响应为 `Content-Type: text/event-stream`，逐步推送事件

#### Scenario: 非流式请求保持原有行为
- **WHEN** 请求 NLQ 端点未指定 SSE header
- **THEN** 保持原有 JSON 响应行为

### Requirement: 多轮对话上下文
系统 SHALL 维护每个会话的对话历史，支持追问。
会话 SHALL 通过请求中的 `X-Session-Id` header 标识。
无 `X-Session-Id` 的请求 SHALL 创建新会话。
对话上下文 SHALL 包含最近 N 轮（默认 5 轮）的问答历史。
会话数据 SHALL 保存在内存 `ConcurrentHashMap` 中，启动时清空。

#### Scenario: 新会话创建
- **WHEN** 首次 NLQ 请求不带 `X-Session-Id` header
- **THEN** 系统为新会话分配 ID，返回 `X-Session-Id` response header

#### Scenario: 追问使用会话历史
- **WHEN** 第二次 NLQ 请求携带 `X-Session-Id` header
- **THEN** 系统将前一轮的对话上下文加入 LLM prompt

#### Scenario: 会话过期
- **WHEN** 会话超过 30 分钟无活动
- **THEN** 会话 SHALL 被清理，下次请求创建新会话
