## 1. 外部化模板配置 (template-driven-nlq)

- [x] 1.1 创建 `src/main/resources/nlq-templates/` 目录，编写 `sample.yml` 和 `university.yml` 模板文件（覆盖 11 个现有规则）
- [x] 1.2 实现 `TemplateLoader` 组件：启动时从 `nlq-templates/{tenantId}.yml` 加载 YAML，解析为规则列表，缓存到内存
- [x] 1.3 重构 `SparqlTemplateGenerator`：优先使用 YAML 模板（如存在），否则 fallback 到原硬编码逻辑
- [x] 1.4 更新 `application.yml` 添加 `ontology.nlq.template-path` 配置项
- [x] 1.5 编写 `SparqlTemplateGeneratorTest` 新增 16 个用例覆盖 YAML 加载、匹配、fallback 场景

## 2. LLM Prompt 优化 (llm-prompt-enhancement)

- [x] 2.1 创建 `nlq-templates/prompt-template.txt`，包含 `{{tenantId}}`、`{{schema}}`、`{{question}}`、`{{examples}}` 占位符
- [x] 2.2 创建 `nlq-templates/sample-examples.yml` 和 `university-examples.yml`，每租户 4 个 few-shot 示例
- [x] 2.3 重构 `NaturalLanguageQueryService.buildLlmPrompt()`：从文件加载 prompt 模板，替换占位符，注入 few-shot 示例
- [x] 2.4 优化 schema 描述格式（带缩进的类层次结构、PREFIX 声明、subProperty 关系）
- [x] 2.5 添加 SPARQL 提取后校验（确保包含 SELECT/CONSTRUCT，拒绝空响应）

## 3. SSE 流式响应 (streaming-nlq)

- [x] 3.1 在 `NlqController` 中新增 `GET /api/v1/tenants/{tenantId}/nlq/stream` 端点，返回 `SseEmitter`
- [x] 3.2 实现 SSE 事件分阶段推送：`status` → `sparql` → `result` → `complete`
- [x] 3.3 添加客户端断连处理（`SseEmitter.onCompletion` / `onTimeout` 回调取消查询）
- [x] 3.4 添加可配置超时时间 `ontology.nlq.stream.timeout`
- [x] 3.5 编写 `NlqControllerTest` 新增 3 个 SSE 端点 MockMvc 测试（阻塞查询、SSE 流式、含 sessionId）

## 4. 会话上下文管理 (conversational-nlq)

- [x] 4.1 实现 `SessionManager` 组件（内存 Map + TTL 过期 + LRU 淘汰）
- [x] 4.2 在 `NaturalLanguageQueryService` 中集成会话历史注入 prompt
- [x] 4.3 添加会话 ID 参数支持（SSE 端点和现有 POST 端点均可选接受 `sessionId`）
- [x] 4.4 实现定期清理过期会话的后台任务（`@Scheduled`）
- [x] 4.5 编写 `SessionManagerTest` 10 个单元测试覆盖创建、复用、过期、淘汰场景

## 5. 验证与清理

- [x] 5.1 运行全量测试：62 个测试全部通过（36 个原有 + 26 个新增），无回归
- [x] 5.2 更新 `docs/roadmap.md` 标记 NLQ 增强为完成
- [x] 5.3 手动验证 blocking NLQ 端点的向后兼容性 — 已有 2 个 NLQ 服务测试通过
