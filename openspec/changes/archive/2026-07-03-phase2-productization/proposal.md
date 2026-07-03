## Why

Phase 1 实现了核心功能（多租户、动态 Schema、审计日志），但所有 API 完全公开、SPARQL 仅支持 JSON 格式、NLQ 模板硬编码且缺乏上下文能力。Phase 2 将这些功能产品化：添加认证鉴权保护敏感端点，支持标准 SPARQL 协议格式协商，将 NLQ 从实验性功能升级为可配置、可扩展的查询入口。

## What Changes

- **API 认证与鉴权**: 引入 Spring Security，支持 API Key (Header) 认证，为敏感端点（reinit、audit-log、clear、tenant CRUD）添加权限控制，开发模式可配置跳过
- **API Key 数据库持久化**: 将 API Key 从 `application.yml` 迁移到数据库，支持运行时 CRUD 管理
- **SPARQL 结果格式多样化**: 根据 `Accept` header 内容协商返回 SPARQL XML / CSV / TSV / JSON-LD / Turtle 格式，支持 CONSTRUCT 查询返回 RDF 图格式
- **NLQ 增强**: 将硬编码 regex 模板改为 YAML 配置（每个租户可自定义），优化 LLM prompt 加入 few-shot 示例，支持流式 SSE 响应，支持多轮对话上下文

## Capabilities

### New Capabilities
- `api-auth`: API 层认证与鉴权 —— API Key 认证 + 端点权限控制 + 开发模式跳过
- `api-key-persistence`: API Key 数据库持久化 —— DB 存储 + 运行时 CRUD 管理
- `sparql-formats`: SPARQL 结果格式协商 —— 支持 SPARQL XML/CSV/TSV/JSON-LD/Turtle 等多种序列化格式
- `nlq-enhance`: NLQ 查询增强 —— 可配置模板 + LLM prompt 优化 + 流式响应 + 多轮对话上下文

### Modified Capabilities
- (无，Phase 1 未生成 main specs)

## Impact

| 领域 | 影响 |
|------|------|
| 新增依赖 | `spring-boot-starter-security` |
| 新增文件 | `SecurityConfig.java`, `ApiKeyFilter.java`, `ApiKeyAuthentication.java`, SPARQL 格式转换工具类、NLQ 模板 YAML 配置文件、NLQ 流式 controller、会话管理 |
| 修改文件 | `pom.xml`, `application.yml`, `SparqlController.java`, `NlqController.java`, `AdminController.java`, `NaturalLanguageQueryService.java`, `SparqlTemplateGenerator.java`, `OntopEngine.java` |
| 测试 | 新增安全集成测试、SPARQL 格式测试、NLQ 模板/流式/多轮测试 |
