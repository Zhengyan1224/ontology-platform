## Context

现有 `OwlGeneratorService` 已能通过 JDBC `DatabaseMetaData` 读取表、列、主键、外键等信息并生成 OWL Turtle。但 OBDA 映射仍须手工编写，与 OWL 之间存在手动同步成本。本项目复⽤同一套 JDBC 元数据提取逻辑，新增 `ObdaGeneratorService` 输出 Ontop OBDA 格式，并通过统一端点一次生成两个文件。

两个示例映射（`exampleBooks.obda` 和 `university.obda`）代表了两种风格：
- **per-element（拆分式）**: 每个类、每个属性独立映射（Books 风格）
- **per-table（混合式）**: 一张表的一个映射包含 class + 所有 datatype + 对象属性（University 风格）

本项目采用 **per-table** 风格，因为更紧凑，且与 `OwlGeneratorService` 的类-属性生成逻辑天然对齐。

## Goals / Non-Goals

**Goals:**
- 读取 JDBC 元数据 → 自动生成 OBDA 映射文件
- 每次映射包含：类声明 + 数据属性 + 对象属性（FK）
- 支持 IRI 模板配置（前缀分隔符风格）
- 支持连接表（只有 FK 组合键的表）特殊处理
- 统一的 `POST /tenants/{id}/generate-mapping` 端点，同时产出 OWL + OBDA
- 在 `OwlGenerationProperties` 中扩展 OBDA 配置项

**Non-Goals:**
- 不支持 OWL 类层次（subClassOf）的自动推导 — 需要通过 overlay 配置手动补充
- 不支持自定义 SPARQL 查询模板 — 只做列→属性的直接映射
- 不支持视图（VIEW）— 只映射物理表
- 不修改现有 `POST /generate-owl` 端点（仅添加 deprecation 标记）

## Decisions

### Decision 1: ObdaGeneratorService 独立于 OwlGeneratorService

共享数据库连接和元数据读取逻辑，但生成逻辑完全分离。两者都依赖 JDBC metadata 读取且产生不同格式输出，若合在一起会形成长方法、难以测试和维护。

**替代方案考虑**: 合并为一个 `MappingGeneratorService`，根据参数输出不同格式 → 否决，因为 OWL 和 OBDA 的输出逻辑差异大，合在一起耦合度高。

### Decision 2: JDBC 元数据提取提取为共享方法

将 `OwlGeneratorService` 中的 `readTables()`, `readColumns()`, `readPrimaryKeys()`, `readForeignKeys()` 等方法提取成独立的工具类或提取到一个共享的 `JdbcMetadataReader` 中，供两个生成器复用。

### Decision 3: per-table 映射风格

每一张表生成一个 OBDA mapping block，包含 class 映射 + 所有列的数据属性 + 外键的对象属性。这与 University 示例的风格一致。

**替代方案考虑**: per-element 拆分（Books 风格）→ 否决，因为 per-table 更简洁，生成更少 mapping block，且更容易与 OWL 中的类-属性定义对应。

### Decision 4: IRI 模板通过配置控制

新增 `iri-template` 配置，支持 `/{pk}`（斜杠风格）和 `-{pk}`（连字符风格）。例如：
- `/{pk}` → `:author/1`
- `-{pk}` → `:emp-001`

### Decision 5: 连接表策略

通过 `join-table-behavior` 配置控制：
- `object-only`（默认）: 连接表不生成类映射，只生成对象属性映射
- `skip`: 完全跳过连接表（不生成任何映射）
- `class-and-object`: 连接表同时生成类映射 + 对象属性

判断连接表的逻辑：如果一张表的全部列都是 FK（且是联合 PK 的一部分），则判定为连接表。

### Decision 6: 保留旧端点

为兼容现有调用方，`POST /tenants/{id}/generate-owl` 端点保留但标注 `@Deprecated`，内部委托给统一端点。

## Risks / Trade-offs

- **[风险] 自动生成的 OBDA 可能需要人工调整** → 生成时添加明确的 `## GENERATED — DO NOT EDIT DIRECTLY` 注释，更改应通过 overlay 配置或修改 DB 来实现
- **[风险] 复杂命名转换可能导致 IRI 冲突** → 内置 singularize 规则可能误判（如 `status` → `Stat`），已有现成规则可继续沿用
- **[取舍] per-table 风格在大型表上会产生很长的 mapping block** → 不影响 Ontop 引擎性能，但可读性下降。如有需要后续可提供 per-element 选项
- **[取舍] DB 数据字典不包含语义信息** → 生成的 property 名称基于列名，可能不够语义化。建议用户通过 `columnToPropertyPrefix` 或 comments 列来改善
