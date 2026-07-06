package org.zhengyan.ontology.platform.service;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.exception.OntologyPlatformException;
import org.zhengyan.ontology.platform.model.Tenant;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.JDBCType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MappingAssistantService {

    private static final Logger log = LoggerFactory.getLogger(MappingAssistantService.class);
    private static final int DEFAULT_PROMPT_LIMIT = 14000;
    private static final Set<String> SENSITIVE_COLUMN_TOKENS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "api_key", "apikey",
            "credential", "salt", "phone", "mobile", "email", "id_card", "ssn");

    private final TenantConfig tenantConfig;
    private final TenantPersistenceService tenantPersistenceService;
    private final OwlGeneratorService owlGeneratorService;
    private final ObdaGeneratorService obdaGeneratorService;
    private final JdbcMetadataReader metadataReader;
    private final ChatLanguageModel llm;
    private final boolean llmAvailable;

    public MappingAssistantService(
            TenantConfig tenantConfig,
            TenantPersistenceService tenantPersistenceService,
            OwlGeneratorService owlGeneratorService,
            ObdaGeneratorService obdaGeneratorService,
            JdbcMetadataReader metadataReader,
            @Value("${ontology.nlq.llm.api-key:}") String apiKey,
            @Value("${ontology.nlq.llm.model:gpt-4o-mini}") String model,
            @Value("${ontology.nlq.llm.base-url:}") String baseUrl) {
        this.tenantConfig = tenantConfig;
        this.tenantPersistenceService = tenantPersistenceService;
        this.owlGeneratorService = owlGeneratorService;
        this.obdaGeneratorService = obdaGeneratorService;
        this.metadataReader = metadataReader;

        if (apiKey != null && !apiKey.isBlank() && !"sk-placeholder".equals(apiKey)) {
            var builder = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(model)
                    .timeout(Duration.ofSeconds(45))
                    .maxRetries(1);
            if (baseUrl != null && !baseUrl.isBlank()) {
                builder.baseUrl(baseUrl);
            }
            this.llm = builder.build();
            this.llmAvailable = true;
            log.info("Mapping assistant LLM initialized: model={}", model);
        } else {
            this.llm = null;
            this.llmAvailable = false;
            log.info("Mapping assistant running in rule-only mode");
        }
    }

    public DraftResponse createDraft(String tenantId, DraftRequest request) {
        DraftRequest resolvedRequest = request != null ? request : DraftRequest.empty();
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            throw OntologyPlatformException.tenantNotFound(tenantId);
        }

        try {
            MetadataSnapshot metadata = readMetadata(tenant);
            String metadataSummary = buildMetadataSummary(metadata);
            String owlDraft = owlGeneratorService.generateOwl(tenant);
            String obdaDraft = obdaGeneratorService.generateObda(tenant);

            List<String> warnings = buildWarnings(metadata);
            List<String> nextSteps = buildNextSteps(metadata);
            String mode = "rules";
            String reviewMarkdown;

            if (resolvedRequest.useLlmOrDefault() && llmAvailable) {
                try {
                    reviewMarkdown = callLlm(tenant, resolvedRequest, metadataSummary, owlDraft, obdaDraft);
                    mode = "llm";
                } catch (Exception e) {
                    log.warn("Mapping assistant LLM call failed, using rule review: {}", e.getMessage());
                    warnings.add("LLM 调用失败，已回退到规则解释：" + e.getClass().getSimpleName());
                    reviewMarkdown = buildRuleReview(tenant, resolvedRequest, metadata, warnings);
                }
            } else {
                if (!llmAvailable) {
                    warnings.add("当前未配置真实 LLM_API_KEY，返回规则生成的解释和检查清单。");
                }
                reviewMarkdown = buildRuleReview(tenant, resolvedRequest, metadata, warnings);
            }

            boolean includeDraftFiles = resolvedRequest.includeDraftFilesOrDefault();
            return new DraftResponse(
                    tenant.getId(),
                    tenant.getName(),
                    mode,
                    llmAvailable,
                    true,
                    false,
                    metadataSummary,
                    reviewMarkdown,
                    warnings,
                    nextSteps,
                    includeDraftFiles ? owlDraft : null,
                    includeDraftFiles ? obdaDraft : null,
                    Instant.now().toString());
        } catch (OntologyPlatformException e) {
            throw e;
        } catch (Exception e) {
            throw new OntologyPlatformException(
                    "Failed to create mapping draft: " + e.getMessage(),
                    500,
                    "MAPPING_DRAFT_FAILED",
                    e);
        }
    }

    private MetadataSnapshot readMetadata(Tenant tenant) throws Exception {
        Class.forName(tenant.getJdbcDriver());
        try (Connection conn = DriverManager.getConnection(
                tenant.getJdbcUrl(),
                tenant.getJdbcUsername(),
                tenant.getJdbcPassword())) {
            List<JdbcMetadataReader.TableInfo> tables = metadataReader.readTables(conn, tenant);
            Set<String> primaryKeys = metadataReader.readPrimaryKeys(conn, tenant);
            return new MetadataSnapshot(tables, primaryKeys);
        }
    }

    private String buildMetadataSummary(MetadataSnapshot metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tables: ").append(metadata.tables().size()).append('\n');
        for (JdbcMetadataReader.TableInfo table : metadata.tables()) {
            sb.append("- ").append(table.name).append(" (")
                    .append(table.columns != null ? table.columns.size() : 0)
                    .append(" columns)\n");
            if (table.comment != null && !table.comment.isBlank()) {
                sb.append("  comment: ").append(table.comment).append('\n');
            }
            if (table.columns == null) {
                continue;
            }
            for (JdbcMetadataReader.ColumnInfo column : table.columns) {
                String key = table.name + "." + column.name;
                sb.append("  - ").append(column.name)
                        .append(" ").append(sqlTypeName(column.sqlType));
                if (metadata.primaryKeys().contains(key)) {
                    sb.append(" PK");
                }
                if (column.isNullable) {
                    sb.append(" nullable");
                }
                if (column.fkTargetTable != null) {
                    sb.append(" FK->").append(column.fkTargetTable);
                }
                if (column.comment != null && !column.comment.isBlank()) {
                    sb.append(" // ").append(column.comment);
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private List<String> buildWarnings(MetadataSnapshot metadata) {
        List<String> warnings = new ArrayList<>();
        warnings.add("这是草稿结果：接口不会写入 OWL/OBDA 文件，也不会更新租户配置。");

        if (metadata.tables().isEmpty()) {
            warnings.add("未读取到业务表，请确认 JDBC URL、schema 权限和数据库用户。");
            return warnings;
        }

        for (JdbcMetadataReader.TableInfo table : metadata.tables()) {
            boolean hasPk = metadata.primaryKeys().stream().anyMatch(pk -> pk.startsWith(table.name + "."));
            if (!hasPk) {
                warnings.add("表 " + table.name + " 未发现主键，IRI 模板可能退化为第一列，需要人工确认。");
            }
            if (table.columns == null) {
                continue;
            }
            for (JdbcMetadataReader.ColumnInfo column : table.columns) {
                String lowered = column.name.toLowerCase(Locale.ROOT);
                for (String token : SENSITIVE_COLUMN_TOKENS) {
                    if (lowered.contains(token)) {
                        warnings.add("字段 " + table.name + "." + column.name
                                + " 可能包含敏感信息，发布到语义层前需要确认权限和脱敏策略。");
                        break;
                    }
                }
            }
        }

        return warnings;
    }

    private List<String> buildNextSteps(MetadataSnapshot metadata) {
        List<String> steps = new ArrayList<>();
        steps.add("由业务负责人确认类名、属性名和对象关系是否符合业务语言。");
        steps.add("确认哪些表和字段应该暴露，敏感字段应删除、脱敏或限制访问。");
        steps.add("检查无主键表、复合主键表和多对多中间表的 IRI 生成规则。");
        steps.add("把草稿保存成新的 .owl/.obda 文件前，先在测试租户上初始化并运行示例 SPARQL。");
        if (metadata.tables().stream().anyMatch(NamingUtils::isJoinTable)) {
            steps.add("发现疑似多对多中间表，请确认是否只需要对象属性映射，而不是独立业务类。");
        }
        return steps;
    }

    private String callLlm(Tenant tenant,
                           DraftRequest request,
                           String metadataSummary,
                           String owlDraft,
                           String obdaDraft) {
        String prompt = buildPrompt(tenant, request, metadataSummary, owlDraft, obdaDraft);
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(UserMessage.from(prompt)))
                .build();
        ChatResponse response = llm.chat(chatRequest);
        return response.aiMessage().text();
    }

    private String buildPrompt(Tenant tenant,
                               DraftRequest request,
                               String metadataSummary,
                               String owlDraft,
                               String obdaDraft) {
        int limit = request.maxPromptCharsOrDefault();
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                You are an OBDA onboarding assistant. Review database metadata and deterministic draft OWL/OBDA files.
                Important constraints:
                - Do not claim that any production file was changed.
                - Treat generated OWL/OBDA as draft only.
                - Focus on business semantics, naming, sensitive fields, missing keys, join tables, and validation steps.
                - Do not output full replacement OWL/OBDA files. Explain suggested changes in markdown.
                - Answer in Chinese.

                """);
        prompt.append("Tenant: ").append(tenant.getId()).append(" / ").append(tenant.getName()).append("\n");
        prompt.append("Focus: ").append(request.focusOrDefault()).append("\n");
        if (request.businessContext() != null && !request.businessContext().isBlank()) {
            prompt.append("Business context:\n").append(request.businessContext()).append("\n\n");
        }
        prompt.append("Database metadata:\n").append(clip(metadataSummary, limit / 3)).append("\n");
        prompt.append("OWL draft excerpt:\n```ttl\n").append(clip(owlDraft, limit / 3)).append("\n```\n");
        prompt.append("OBDA draft excerpt:\n```text\n").append(clip(obdaDraft, limit / 3)).append("\n```\n");
        prompt.append("""

                Please return markdown with these sections:
                1. 草稿摘要
                2. 命名和业务语义建议
                3. 敏感字段与权限风险
                4. 需要人工确认的问题
                5. 上线前验证步骤
                """);
        return clip(prompt.toString(), limit);
    }

    private String buildRuleReview(Tenant tenant,
                                   DraftRequest request,
                                   MetadataSnapshot metadata,
                                   List<String> warnings) {
        String tableNames = metadata.tables().stream()
                .map(t -> "`" + t.name + "`")
                .collect(Collectors.joining(", "));
        if (tableNames.isBlank()) {
            tableNames = "无";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 草稿摘要\n");
        sb.append("- 租户：`").append(tenant.getId()).append("`");
        if (tenant.getName() != null) {
            sb.append("（").append(tenant.getName()).append("）");
        }
        sb.append('\n');
        sb.append("- 表数量：").append(metadata.tables().size()).append('\n');
        sb.append("- 表清单：").append(tableNames).append('\n');
        sb.append("- 生成方式：基于 JDBC 元数据的规则生成");
        if (request.focusOrDefault() != null) {
            sb.append("，关注点：").append(request.focusOrDefault());
        }
        sb.append("\n\n");

        sb.append("## 命名和业务语义建议\n");
        sb.append("- 表名会被转换为 OWL Class，字段会被转换为 DatatypeProperty 或 ObjectProperty。\n");
        sb.append("- 技术化表名、缩写字段和字典字段需要业务人员确认，例如状态、类型、编码类字段。\n");
        sb.append("- 外键只说明数据库引用关系，不一定等同于业务语义关系，关系名称需要人工复核。\n\n");

        sb.append("## 敏感字段与权限风险\n");
        for (String warning : warnings) {
            sb.append("- ").append(warning).append('\n');
        }
        sb.append('\n');

        sb.append("## 需要人工确认的问题\n");
        sb.append("- 哪些表是核心业务实体，哪些只是日志、配置、关联或临时表。\n");
        sb.append("- 无主键或复合主键表如何生成稳定 IRI。\n");
        sb.append("- 是否需要隐藏手机号、邮箱、token、密码、证件号等字段。\n");
        sb.append("- 多对多中间表是否应建成对象关系，而不是独立类。\n\n");

        sb.append("## 上线前验证步骤\n");
        sb.append("- 把草稿保存到测试 OWL/OBDA 文件。\n");
        sb.append("- 新建测试租户并初始化 Ontop 引擎。\n");
        sb.append("- 运行代表性 SPARQL，确认返回数据、SQL 改写和性能。\n");
        sb.append("- 业务负责人和数据安全负责人确认后，再替换生产映射。\n");
        return sb.toString();
    }

    private Tenant findTenant(String tenantId) {
        List<Tenant> all = new ArrayList<>(tenantConfig.getTenants());
        for (Tenant persisted : tenantPersistenceService.findAll()) {
            if (all.stream().noneMatch(t -> t.getId().equals(persisted.getId()))) {
                all.add(persisted);
            }
        }
        return all.stream()
                .filter(t -> t.getId().equals(tenantId))
                .findFirst()
                .orElse(null);
    }

    private String sqlTypeName(int sqlType) {
        try {
            return JDBCType.valueOf(sqlType).getName();
        } catch (Exception e) {
            return "SQL_TYPE_" + sqlType;
        }
    }

    private String clip(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars)) + "\n...[truncated]";
    }

    private record MetadataSnapshot(List<JdbcMetadataReader.TableInfo> tables, Set<String> primaryKeys) {
    }

    public record DraftRequest(String businessContext,
                               String focus,
                               Boolean includeDraftFiles,
                               Boolean useLlm,
                               Integer maxPromptChars) {
        public static DraftRequest empty() {
            return new DraftRequest(null, null, true, true, DEFAULT_PROMPT_LIMIT);
        }

        public boolean includeDraftFilesOrDefault() {
            return includeDraftFiles == null || includeDraftFiles;
        }

        public boolean useLlmOrDefault() {
            return useLlm == null || useLlm;
        }

        public String focusOrDefault() {
            return focus == null || focus.isBlank() ? "all" : focus;
        }

        public int maxPromptCharsOrDefault() {
            if (maxPromptChars == null || maxPromptChars < 4000) {
                return DEFAULT_PROMPT_LIMIT;
            }
            return Math.min(maxPromptChars, 30000);
        }
    }

    public record DraftResponse(String tenantId,
                                String tenantName,
                                String mode,
                                boolean llmAvailable,
                                boolean draftOnly,
                                boolean applied,
                                String metadataSummary,
                                String reviewMarkdown,
                                List<String> warnings,
                                List<String> nextSteps,
                                String owlDraft,
                                String obdaDraft,
                                String generatedAt) {
    }
}
