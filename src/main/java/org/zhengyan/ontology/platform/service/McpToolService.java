package org.zhengyan.ontology.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.repository.TenantContentRepository;
import org.zhengyan.ontology.platform.repository.RuleRepository;
import org.zhengyan.ontology.platform.repository.ActionRepository;
import org.zhengyan.ontology.platform.repository.WorkflowRepository;
import org.zhengyan.ontology.platform.model.Rule;
import org.zhengyan.ontology.platform.model.Workflow;
import org.zhengyan.ontology.platform.model.Action;
import org.zhengyan.ontology.platform.model.Document;
import org.zhengyan.ontology.platform.model.OntologyProposal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class McpToolService {

    private static final Logger log = LoggerFactory.getLogger(McpToolService.class);

    private final TenantPersistenceService tenantPersistenceService;
    private final EngineRegistry engineRegistry;
    private final CachedSparqlService cachedSparqlService;
    private final NaturalLanguageQueryService nlqService;
    private final SqlExecutionService sqlExecutionService;
    private final TenantConfig tenantConfig;
    private final TenantContentRepository tenantContentRepository;
    private final RuleRepository ruleRepository;
    private final RuleService ruleService;
    private final RuleTriggerService ruleTriggerService;
    private final ActionRepository actionRepository;
    private final ActionService actionService;
    private final WorkflowRepository workflowRepository;
    private final WorkflowService workflowService;
    private final DocumentIngestionService documentIngestionService;
    private final LlmOntologyAssistService llmOntologyAssistService;
    private final ObjectMapper objectMapper;

    public McpToolService(
            TenantPersistenceService tenantPersistenceService,
            EngineRegistry engineRegistry,
            CachedSparqlService cachedSparqlService,
            NaturalLanguageQueryService nlqService,
            SqlExecutionService sqlExecutionService,
            TenantConfig tenantConfig,
            TenantContentRepository tenantContentRepository,
            RuleRepository ruleRepository,
            RuleService ruleService,
            RuleTriggerService ruleTriggerService,
            ActionRepository actionRepository,
            ActionService actionService,
            WorkflowRepository workflowRepository,
            WorkflowService workflowService,
            DocumentIngestionService documentIngestionService,
            LlmOntologyAssistService llmOntologyAssistService,
            ObjectMapper objectMapper) {
        this.tenantPersistenceService = tenantPersistenceService;
        this.engineRegistry = engineRegistry;
        this.cachedSparqlService = cachedSparqlService;
        this.nlqService = nlqService;
        this.sqlExecutionService = sqlExecutionService;
        this.tenantConfig = tenantConfig;
        this.tenantContentRepository = tenantContentRepository;
        this.ruleRepository = ruleRepository;
        this.ruleService = ruleService;
        this.ruleTriggerService = ruleTriggerService;
        this.actionRepository = actionRepository;
        this.actionService = actionService;
        this.workflowRepository = workflowRepository;
        this.workflowService = workflowService;
        this.documentIngestionService = documentIngestionService;
        this.llmOntologyAssistService = llmOntologyAssistService;
        this.objectMapper = objectMapper;
    }

    public List<McpServerFeatures.SyncToolSpecification> getTools() {
        return List.of(
                tenantListTool(),
                tenantInfoTool(),
                sparqlQueryTool(),
                sqlQueryTool(),
                nlqQueryTool(),
                ruleListTool(),
                ruleEvaluateTool(),
                actionListTool(),
                actionExecuteTool(),
                workflowListTool(),
                workflowExecuteTool(),
                documentListTool(),
                documentQueryTool(),
                ontologyExtractTool(),
                ontologyApplyProposalTool()
        );
    }

    public List<McpServerFeatures.SyncResourceSpecification> getResources() {
        return List.of(
                tenantsHealthResource()
        );
    }

    public List<McpServerFeatures.SyncResourceTemplateSpecification> getResourceTemplates() {
        return List.of(
                ontologyResourceTemplate(),
                mappingResourceTemplate()
        );
    }

    private McpServerFeatures.SyncToolSpecification tenantListTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("tenant_list")
                        .description("List all tenants with their health status")
                        .inputSchema(emptySchema())
                        .build())
                .callHandler((exchange, request) -> {
                    List<Map<String, Object>> tenantList = buildTenantList();
                    return McpSchema.CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent(toJson(tenantList))))
                            .build();
                })
                .build();
    }

    private McpServerFeatures.SyncToolSpecification tenantInfoTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("tenant_info")
                        .description("Get detailed information about a tenant")
                        .inputSchema(objectSchema(
                                reqProp("tenantId", "Tenant ID (e.g. sample, university)")
                        ))
                        .build())
                .callHandler((exchange, request) -> {
                    String tenantId = arg(request, "tenantId");
                    try {
                        Tenant tenant = findTenant(tenantId);
                        Map<String, Object> info = new HashMap<>();
                        info.put("id", tenant.getId());
                        info.put("name", tenant.getName());
                        info.put("jdbcUrl", tenant.getJdbcUrl());
                        info.put("healthy", engineRegistry.isHealthy(tenantId));
                        info.put("hasOwlContent", tenant.getOwlContent() != null || tenant.getOwlPath() != null);
                        info.put("hasObdaContent", tenant.getObdaContent() != null || tenant.getObdaPath() != null);
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(toJson(info))))
                                .build();
                    } catch (Exception e) {
                        return error("Tenant not found: " + tenantId);
                    }
                })
                .build();
    }

    private McpServerFeatures.SyncToolSpecification sparqlQueryTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("sparql_query")
                        .description("Execute a SPARQL query against a tenant's ontology")
                        .inputSchema(objectSchema(
                                reqProp("tenantId", "Tenant ID (e.g. sample, university)"),
                                reqProp("query", "SPARQL query to execute")
                        ))
                        .build())
                .callHandler((exchange, request) -> {
                    String tenantId = arg(request, "tenantId");
                    String query = arg(request, "query");
                    try {
                        SparqlQueryResult result = cachedSparqlService.executeQuery(tenantId, query);
                        Map<String, Object> response = new HashMap<>();
                        response.put("variables", result.getVariables());
                        response.put("results", result.getResults());
                        response.put("executionTimeMs", result.getExecutionTimeMs());
                        response.put("translatedSql", result.getTranslatedSql());
                        response.put("queryType", result.getQueryType().name());
                        if (result.isBooleanResult()) {
                            response.put("booleanResult", result.isBooleanQueryResult());
                        }
                        int resultCount = result.isBooleanResult() ? 1 :
                                result.isGraphResult() ? result.getGraphModel().size() : result.getResults().size();
                        ruleTriggerService.onSparqlQuery(tenantId, query, 0, resultCount);
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(toJson(response))))
                                .build();
                    } catch (Exception e) {
                        log.warn("SPARQL query failed for [{}]: {}", tenantId, e.getMessage());
                        return error("SPARQL query failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private McpServerFeatures.SyncToolSpecification sqlQueryTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("sql_query")
                        .description("Execute a SQL query against a tenant's database (SELECT only)")
                        .inputSchema(objectSchema(
                                reqProp("tenantId", "Tenant ID (e.g. sample, university)"),
                                reqProp("sql", "SQL query to execute")
                        ))
                        .build())
                .callHandler((exchange, request) -> {
                    String tenantId = arg(request, "tenantId");
                    String sql = arg(request, "sql");
                    try {
                        Tenant tenant = findTenant(tenantId);
                        SqlExecutionService.SqlResult result = sqlExecutionService.execute(tenant, sql);
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", result.success());
                        response.put("columns", result.columns());
                        response.put("rows", result.rows());
                        response.put("rowCount", result.rowCount());
                        response.put("executionTimeMs", result.executionTimeMs());
                        if (result.error() != null) {
                            response.put("error", result.error());
                        }
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(toJson(response))))
                                .build();
                    } catch (Exception e) {
                        log.warn("SQL query failed for [{}]: {}", tenantId, e.getMessage());
                        return error("SQL query failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private McpServerFeatures.SyncToolSpecification nlqQueryTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("nlq_query")
                        .description("Translate a natural language question into SPARQL and execute it")
                        .inputSchema(objectSchema(
                                reqProp("tenantId", "Tenant ID (e.g. sample, university)"),
                                reqProp("question", "Natural language question about the data")
                        ))
                        .build())
                .callHandler((exchange, request) -> {
                    String tenantId = arg(request, "tenantId");
                    String question = arg(request, "question");
                    try {
                        NlqResult nlqResult = nlqService.answer(tenantId, question);
                        Map<String, Object> response = new HashMap<>();
                        response.put("question", nlqResult.getQuestion());
                        response.put("sparql", nlqResult.getSparql());
                        response.put("mode", nlqResult.getMode());
                        response.put("variables", nlqResult.getVariables());
                        response.put("results", nlqResult.getResults());
                        response.put("executionTimeMs", nlqResult.getExecutionTimeMs());
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(toJson(response))))
                                .build();
                    } catch (Exception e) {
                        log.warn("NLQ query failed for [{}]: {}", tenantId, e.getMessage());
                        return error("NLQ query failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private McpServerFeatures.SyncResourceSpecification tenantsHealthResource() {
        return new McpServerFeatures.SyncResourceSpecification(
                McpSchema.Resource.builder()
                        .uri("tenants://health")
                        .name("Tenants Health")
                        .description("List of all tenants and their health status")
                        .mimeType("application/json")
                        .build(),
                (exchange, request) -> {
                    List<Map<String, Object>> tenantList = buildTenantList();
                    return McpSchema.ReadResourceResult.builder(List.of(
                            new McpSchema.TextResourceContents(
                                    "tenants://health",
                                    "application/json",
                                    toJson(tenantList))
                    )).build();
                }
        );
    }

    private McpServerFeatures.SyncResourceTemplateSpecification ontologyResourceTemplate() {
        return new McpServerFeatures.SyncResourceTemplateSpecification(
                McpSchema.ResourceTemplate.builder()
                        .uriTemplate("ontology://{tenantId}")
                        .name("Ontology OWL Content")
                        .description("OWL ontology content for a tenant")
                        .mimeType("text/turtle")
                        .build(),
                (exchange, request) -> {
                    String tenantId = extractTenantId(request.uri());
                    String owlContent = loadOwlContent(tenantId);
                    if (owlContent == null) owlContent = "";
                    return McpSchema.ReadResourceResult.builder(List.of(
                            new McpSchema.TextResourceContents(
                                    "ontology://" + tenantId,
                                    "text/turtle",
                                    owlContent)
                    )).build();
                }
        );
    }

    private McpServerFeatures.SyncResourceTemplateSpecification mappingResourceTemplate() {
        return new McpServerFeatures.SyncResourceTemplateSpecification(
                McpSchema.ResourceTemplate.builder()
                        .uriTemplate("ontology://{tenantId}/mapping")
                        .name("OBDA Mapping")
                        .description("OBDA mapping content for a tenant")
                        .mimeType("text/plain")
                        .build(),
                (exchange, request) -> {
                    String tenantId = extractTenantId(request.uri());
                    String obdaContent = loadObdaContent(tenantId);
                    if (obdaContent == null) obdaContent = "";
                    return McpSchema.ReadResourceResult.builder(List.of(
                            new McpSchema.TextResourceContents(
                                    "ontology://" + tenantId + "/mapping",
                                    "text/plain",
                                    obdaContent)
                    )).build();
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification ruleListTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("rule_list")
                        .description("List all business rules for a tenant")
                        .inputSchema(objectSchema(
                                reqProp("tenantId", "Tenant ID (e.g. sample, university)")
                        ))
                        .build())
                .callHandler((exchange, request) -> {
                    String tenantId = arg(request, "tenantId");
                    try {
                        List<Rule> rules = ruleRepository.findByTenantId(tenantId);
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(toJson(rules))))
                                .build();
                    } catch (Exception e) {
                        return error("Failed to list rules: " + e.getMessage());
                    }
                })
                .build();
    }

    private McpServerFeatures.SyncToolSpecification ruleEvaluateTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("rule_evaluate")
                        .description("Evaluate a business rule with a given context")
                        .inputSchema(objectSchema(
                                reqProp("tenantId", "Tenant ID (e.g. sample, university)"),
                                reqProp("ruleId", "Rule ID to evaluate"),
                                reqProp("context", "JSON object with evaluation context variables")
                        ))
                        .build())
                .callHandler((exchange, request) -> {
                    String tenantId = arg(request, "tenantId");
                    String ruleId = arg(request, "ruleId");
                    String contextStr = arg(request, "context");
                    try {
                        Rule rule = ruleRepository.findById(ruleId);
                        if (rule == null) {
                            return error("Rule not found: " + ruleId);
                        }
                        @SuppressWarnings("unchecked")
                        Map<String, Object> context = objectMapper.readValue(contextStr, Map.class);
                        RuleService.EvaluateResult result = ruleService.evaluate(rule, context);
                        Map<String, Object> response = new HashMap<>();
                        response.put("passed", result.passed());
                        response.put("trace", result.trace());
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(toJson(response))))
                                .build();
                    } catch (Exception e) {
                        return error("Rule evaluation failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private McpServerFeatures.SyncToolSpecification actionListTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("action_list")
                        .description("List all actions for a tenant")
                        .inputSchema(objectSchema(
                                reqProp("tenantId", "Tenant ID (e.g. sample, university)")
                        ))
                        .build())
                .callHandler((exchange, request) -> {
                    String tenantId = arg(request, "tenantId");
                    try {
                        List<Action> actions = actionRepository.findByTenantId(tenantId);
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(toJson(actions))))
                                .build();
                    } catch (Exception e) {
                        return error("Failed to list actions: " + e.getMessage());
                    }
                })
                .build();
    }

    private McpServerFeatures.SyncToolSpecification actionExecuteTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("action_execute")
                        .description("Execute an action with optional dry-run mode")
                        .inputSchema(objectSchema(
                                reqProp("tenantId", "Tenant ID (e.g. sample, university)"),
                                reqProp("actionId", "Action ID to execute"),
                                optProp("dryRun", "Set to 'true' for dry-run (default: false)")
                        ))
                        .build())
                .callHandler((exchange, request) -> {
                    String tenantId = arg(request, "tenantId");
                    String actionId = arg(request, "actionId");
                    boolean dryRun = "true".equals(arg(request, "dryRun"));
                    try {
                        Action action = actionRepository.findById(actionId);
                        if (action == null) {
                            return error("Action not found: " + actionId);
                        }
                        ActionService.ActionResult result = actionService.execute(action, dryRun);
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", result.success());
                        response.put("message", result.message());
                        response.put("details", result.details());
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(toJson(response))))
                                .build();
                    } catch (Exception e) {
                        return error("Action execution failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private McpServerFeatures.SyncToolSpecification workflowListTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("workflow_list")
                        .description("List all workflows for a tenant")
                        .inputSchema(objectSchema(
                                reqProp("tenantId", "Tenant ID (e.g. sample, university)")
                        ))
                        .build())
                .callHandler((exchange, request) -> {
                    String tenantId = arg(request, "tenantId");
                    try {
                        List<Workflow> workflows = workflowRepository.findByTenantId(tenantId);
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(toJson(workflows))))
                                .build();
                    } catch (Exception e) {
                        return error("Failed to list workflows: " + e.getMessage());
                    }
                })
                .build();
    }

    private McpServerFeatures.SyncToolSpecification workflowExecuteTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("workflow_execute")
                        .description("Execute a workflow")
                        .inputSchema(objectSchema(
                                reqProp("tenantId", "Tenant ID (e.g. sample, university)"),
                                reqProp("workflowId", "Workflow ID to execute")
                        ))
                        .build())
                .callHandler((exchange, request) -> {
                    String tenantId = arg(request, "tenantId");
                    String workflowId = arg(request, "workflowId");
                    try {
                        Workflow workflow = workflowRepository.findById(workflowId);
                        if (workflow == null) {
                            return error("Workflow not found: " + workflowId);
                        }
                        WorkflowService.WorkflowResult result = workflowService.execute(workflow);
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", result.success());
                        response.put("message", result.message());
                        response.put("steps", result.steps());
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(toJson(response))))
                                .build();
                    } catch (Exception e) {
                        return error("Workflow execution failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private McpServerFeatures.SyncToolSpecification documentListTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("document_list")
                        .description("List all uploaded documents for a tenant")
                        .inputSchema(objectSchema(
                                reqProp("tenantId", "Tenant ID (e.g. sample, university)")
                        ))
                        .build())
                .callHandler((exchange, request) -> {
                    String tenantId = arg(request, "tenantId");
                    try {
                        List<Document> docs = documentIngestionService.listDocuments(tenantId);
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(toJson(docs))))
                                .build();
                    } catch (Exception e) {
                        return error("Failed to list documents: " + e.getMessage());
                    }
                })
                .build();
    }

    private McpServerFeatures.SyncToolSpecification documentQueryTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("document_query")
                        .description("Search document chunks by semantic similarity to a query")
                        .inputSchema(objectSchema(
                                reqProp("tenantId", "Tenant ID (e.g. sample, university)"),
                                reqProp("query", "Search query text"),
                                optProp("topK", "Number of results to return (default: 5)")
                        ))
                        .build())
                .callHandler((exchange, request) -> {
                    String tenantId = arg(request, "tenantId");
                    String query = arg(request, "query");
                    String topKStr = arg(request, "topK");
                    int topK = 5;
                    if (topKStr != null) {
                        try { topK = Integer.parseInt(topKStr); } catch (NumberFormatException ignored) {}
                    }
                    try {
                        var results = documentIngestionService.queryDocuments(tenantId, query, topK);
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(toJson(results))))
                                .build();
                    } catch (Exception e) {
                        return error("Document query failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private McpServerFeatures.SyncToolSpecification ontologyExtractTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("ontology_extract")
                        .description("Extract ontology concepts from a natural language domain description")
                        .inputSchema(objectSchema(
                                reqProp("tenantId", "Tenant ID (e.g. sample, university)"),
                                reqProp("description", "Domain description for ontology extraction"),
                                optProp("title", "Title for the proposal (default: Ontology Proposal)")
                        ))
                        .build())
                .callHandler((exchange, request) -> {
                    String tenantId = arg(request, "tenantId");
                    String description = arg(request, "description");
                    String title = arg(request, "title");
                    if (title == null || title.isBlank()) title = "Ontology Proposal";
                    try {
                        OntologyProposal proposal = llmOntologyAssistService.extractFromDescription(tenantId, title, description);
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(toJson(proposal))))
                                .build();
                    } catch (Exception e) {
                        return error("Ontology extraction failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private McpServerFeatures.SyncToolSpecification ontologyApplyProposalTool() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder("ontology_apply_proposal")
                        .description("Apply an ontology proposal by merging it into the tenant's OWL/OBDA content")
                        .inputSchema(objectSchema(
                                reqProp("tenantId", "Tenant ID (e.g. sample, university)"),
                                reqProp("proposalId", "Proposal ID to apply")
                        ))
                        .build())
                .callHandler((exchange, request) -> {
                    String tenantId = arg(request, "tenantId");
                    String proposalId = arg(request, "proposalId");
                    try {
                        OntologyProposal result = llmOntologyAssistService.applyProposal(tenantId, proposalId);
                        if (result == null) {
                            return error("Proposal not found: " + proposalId);
                        }
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(toJson(result))))
                                .build();
                    } catch (Exception e) {
                        return error("Failed to apply proposal: " + e.getMessage());
                    }
                })
                .build();
    }

    private static PropDef optProp(String name, String description) {
        return new PropDef(name, stringSchema(description), false);
    }

    private List<Map<String, Object>> buildTenantList() {
        List<Tenant> allTenants = new ArrayList<>(tenantConfig.getTenants());
        try {
            allTenants.addAll(tenantPersistenceService.findAll().stream()
                    .filter(t -> allTenants.stream().noneMatch(existing -> existing.getId().equals(t.getId())))
                    .toList());
        } catch (Exception e) {
            log.warn("Failed to load tenants from DB: {}", e.getMessage());
        }
        return allTenants.stream().map(t -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", t.getId());
            entry.put("name", t.getName());
            entry.put("healthy", engineRegistry.isHealthy(t.getId()));
            return entry;
        }).collect(Collectors.toList());
    }

    private String loadOwlContent(String tenantId) {
        try {
            Tenant tenant = findTenant(tenantId);
            if (tenant.getOwlContent() != null) {
                return tenant.getOwlContent();
            }
        } catch (Exception ignored) {
        }
        try {
            TenantContentRepository.TenantContent content = tenantContentRepository.findByTenantId(tenantId);
            if (content != null) {
                return content.owlContent();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String loadObdaContent(String tenantId) {
        try {
            Tenant tenant = findTenant(tenantId);
            if (tenant.getObdaContent() != null) {
                return tenant.getObdaContent();
            }
        } catch (Exception ignored) {
        }
        try {
            TenantContentRepository.TenantContent content = tenantContentRepository.findByTenantId(tenantId);
            if (content != null) {
                return content.obdaContent();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Tenant findTenant(String tenantId) {
        return tenantConfig.getTenants().stream()
                .filter(t -> t.getId().equals(tenantId))
                .findFirst()
                .orElseGet(() -> tenantPersistenceService.findById(tenantId));
    }

    private static String extractTenantId(String uri) {
        if (uri.startsWith("ontology://")) {
            String rest = uri.substring("ontology://".length());
            if (rest.endsWith("/mapping")) {
                return rest.substring(0, rest.length() - "/mapping".length());
            }
            return rest;
        }
        return uri;
    }

    private static String arg(McpSchema.CallToolRequest request, String name) {
        Object val = request.arguments().get(name);
        return val == null ? null : val.toString();
    }

    private static McpSchema.CallToolResult error(String message) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(message)))
                .isError(true)
                .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"serialization failed\"}";
        }
    }

    private static McpSchema.JsonSchema emptySchema() {
        return McpSchema.JsonSchema.builder()
                .type("object")
                .properties(Map.of())
                .required(List.of())
                .build();
    }

    private static McpSchema.JsonSchema objectSchema(PropDef... props) {
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        for (PropDef p : props) {
            properties.put(p.name, p.schema);
            if (p.required) {
                required.add(p.name);
            }
        }
        return McpSchema.JsonSchema.builder()
                .type("object")
                .properties(properties)
                .required(required)
                .build();
    }

    private static PropDef reqProp(String name, String description) {
        return new PropDef(name, stringSchema(description), true);
    }

    private static Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private record PropDef(String name, Map<String, Object> schema, boolean required) {}
}
