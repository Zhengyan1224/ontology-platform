## Why

手工编写 OBDA 映射文件既痛苦又容易出错——用户需要为每张表、每个列、每个外键手写映射，且必须与 OWL 本体保持严格一致。已有的 `OwlGeneratorService` 已能从 DB 元数据自动生成 OWL，但 OBDA 映射仍需手工维护，两者极易脱节。本项目将 OBDA 生成自动化，使 OWL 和 OBDA 共享同一套 JDBC 元数据源，彻底消除不一致。

## What Changes

- 新建 `ObdaGeneratorService`，通过 JDBC Metadata 自动生成完整 OBDA 映射文件
- 扩展 `OwlGenerationProperties`，增加 OBDA 相关的配置项（IRI 模板风格、连接表处理策略、映射风格）
- 将现有 `POST /tenants/{id}/generate-owl` 端点升级为 `POST /tenants/{id}/generate-mapping`，一次调用同时产出 OWL + OBDA
- 保留旧端点并标记为 deprecation（兼容现有调用方）
- 引入 `join-table-behavior` 策略处理多对多连接表（不生成类映射，只生成对象属性）
- 支持启动时自动生成映射并加载（可选），实现"DB schema 即真理"

## Capabilities

### New Capabilities
- `obda-generation`: 从 JDBC 元数据自动生成 Ontop OBDA 映射文件，包含类映射、数据属性、对象属性、IRI 模板

### Modified Capabilities
- `sql-ddl-to-owl`: 将现有只生成 OWL 的端点扩展为同时生成 OWL + OBDA，并在 `OwlGenerationProperties` 中增加 OBDA 配置项

## Impact

- **新建**: `ObdaGeneratorService.java` — 核心 OBDA 生成逻辑
- **修改**: `OwlGenerationProperties.java` — 扩展配置项
- **修改**: `AdminController.java` 或相关 Controller — 新增 `generate-mapping` 端点
- **修改**: `OwlGeneratorService.java` — 重构共享 JDBC 元数据提取逻辑，与 ObdaGeneratorService 复用
- **无新依赖**: RDF4J 已是传递依赖，OBDA 生成是纯文本拼接
