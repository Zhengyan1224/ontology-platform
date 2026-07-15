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
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.model.OntologyProposal;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.repository.OntologyProposalRepository;
import org.zhengyan.ontology.platform.repository.TenantContentRepository;

import java.sql.*;
import java.time.Duration;
import java.util.*;

@Service
public class LlmOntologyAssistService {

    private static final Logger log = LoggerFactory.getLogger(LlmOntologyAssistService.class);

    private static final String EXTRACT_PROMPT = """
            You are an ontology engineer for an OBDA (Ontology-Based Data Access) system.
            Given a description of a domain, generate OWL ontology content (in Turtle format) and
            an OBDA mapping file that maps the ontology to a relational database.

            The OWL should define classes and object properties for the domain concepts.
            Use the prefix `ex:` for `http://example.org/ontology/`.
            The OBDA mapping should use the R2RML/Target syntax supported by Ontop.

            IMPORTANT: Return ONLY a JSON object with two fields: "owl" and "obda".
            Do not include any explanation or markdown formatting outside the JSON.
            Format:
            {
              "owl": "@prefix ex: <http://example.org/ontology/> .\\n...",
              "obda": "[PrefixDeclaration]\\nex: http://example.org/ontology/\\n..."
            }

            Domain description: %s
            """;

    private static final String DDL_EXTRACT_PROMPT = """
            You are an ontology engineer for an OBDA (Ontology-Based Data Access) system.
            Given the following database schema, generate OWL ontology content (in Turtle format) and
            an OBDA mapping file that maps the ontology to the database tables.

            Follow these conventions:
            - Each table should map to a class with the same name (PascalCase)
            - Each column should map to a data property
            - Foreign keys should map to object properties
            - Use the prefix `ex:` for `http://example.org/ontology/`
            - The OBDA mapping should use Ontop-compatible syntax

            IMPORTANT: Return ONLY a JSON object with two fields: "owl" and "obda".
            Format:
            {
              "owl": "@prefix ex: <http://example.org/ontology/> .\\n...",
              "obda": "[PrefixDeclaration]\\nex: http://example.org/ontology/\\n..."
            }

            Database schema:
            %s
            """;

    private final OntologyProposalRepository proposalRepository;
    private final TenantContentRepository tenantContentRepository;
    private final TenantConfig tenantConfig;
    private final EngineRegistry engineRegistry;
    private final ChatLanguageModel llm;
    private final boolean llmAvailable;

    public LlmOntologyAssistService(
            OntologyProposalRepository proposalRepository,
            TenantContentRepository tenantContentRepository,
            TenantConfig tenantConfig,
            EngineRegistry engineRegistry,
            @Value("${ontology.nlq.llm.api-key:}") String apiKey,
            @Value("${ontology.nlq.llm.model:gpt-4o-mini}") String model,
            @Value("${ontology.nlq.llm.base-url:}") String baseUrl) {
        this.proposalRepository = proposalRepository;
        this.tenantContentRepository = tenantContentRepository;
        this.tenantConfig = tenantConfig;
        this.engineRegistry = engineRegistry;

        if (apiKey != null && !apiKey.isBlank() && !"sk-placeholder".equals(apiKey)) {
            var builder = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(model)
                    .timeout(Duration.ofSeconds(60))
                    .maxRetries(1);
            if (baseUrl != null && !baseUrl.isBlank()) {
                builder.baseUrl(baseUrl);
            }
            this.llm = builder.build();
            this.llmAvailable = true;
            log.info("LLM Ontology Assist initialized: model={}", model);
        } else {
            this.llm = null;
            this.llmAvailable = false;
            log.info("LLM Ontology Assist running in fallback mode (template-only)");
        }
    }

    public OntologyProposal extractFromDescription(String tenantId, String title, String description) {
        OntologyProposal proposal = new OntologyProposal();
        proposal.setTenantId(tenantId);
        proposal.setTitle(title);
        proposal.setDescription(description);
        proposal.setStatus("draft");
        proposal.setSource(llmAvailable ? "llm_extract" : "manual");

        if (llmAvailable) {
            try {
                String prompt = String.format(EXTRACT_PROMPT, description);
                UserMessage userMsg = UserMessage.from(prompt);
                ChatRequest request = ChatRequest.builder()
                        .messages(List.of(userMsg))
                        .build();
                ChatResponse response = llm.chat(request);
                LlmResult parsed = parseLlmResponse(response.aiMessage().text());
                proposal.setProposedOwl(parsed.owl);
                proposal.setProposedObda(parsed.obda);
            } catch (Exception e) {
                log.warn("LLM extraction failed for tenant [{}]: {}", tenantId, e.getMessage());
                proposal.setProposedOwl("// LLM extraction failed: " + e.getMessage());
                proposal.setProposedObda("// LLM extraction failed: " + e.getMessage());
            }
        } else {
            String fallbackOwl = generateFallbackOwl(title, description);
            String fallbackObda = generateFallbackObda();
            proposal.setProposedOwl(fallbackOwl);
            proposal.setProposedObda(fallbackObda);
        }

        proposalRepository.save(proposal);
        return proposal;
    }

    public DdlHintsResult getDdlHints(String tenantId) {
        Tenant tenant = findTenant(tenantId);
        if (tenant == null) {
            return new DdlHintsResult(false, "Tenant not found", List.of(), null);
        }
        List<TableInfo> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(
                tenant.getJdbcUrl(), tenant.getJdbcUsername(), tenant.getJdbcPassword())) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet tablesRs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tablesRs.next()) {
                    String schema = tablesRs.getString("TABLE_SCHEM");
                    String tableName = tablesRs.getString("TABLE_NAME");
                    if (tableName != null && !tableName.toUpperCase().startsWith("TB_")) {
                        tableName = tableName.toUpperCase();
                    }
                    List<ColumnInfo> columns = new ArrayList<>();
                    try (ResultSet colsRs = meta.getColumns(null, schema, tableName, "%")) {
                        while (colsRs.next()) {
                            String colName = colsRs.getString("COLUMN_NAME");
                            String colType = colsRs.getString("TYPE_NAME");
                            int colSize = colsRs.getInt("COLUMN_SIZE");
                            boolean nullable = "YES".equals(colsRs.getString("IS_NULLABLE"));
                            columns.add(new ColumnInfo(colName, colType, colSize, nullable));
                        }
                    }
                    List<String> pkColumns = new ArrayList<>();
                    try (ResultSet pkRs = meta.getPrimaryKeys(null, schema, tableName)) {
                        while (pkRs.next()) {
                            pkColumns.add(pkRs.getString("COLUMN_NAME"));
                        }
                    }
                    List<ForeignKeyInfo> fks = new ArrayList<>();
                    try (ResultSet fkRs = meta.getImportedKeys(null, schema, tableName)) {
                        while (fkRs.next()) {
                            fks.add(new ForeignKeyInfo(
                                    fkRs.getString("FKCOLUMN_NAME"),
                                    fkRs.getString("PKTABLE_NAME"),
                                    fkRs.getString("PKCOLUMN_NAME")));
                        }
                    }
                    tables.add(new TableInfo(tableName, schema, columns, pkColumns, fks));
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to get DDL hints for tenant [{}]: {}", tenantId, e.getMessage());
            return new DdlHintsResult(false, e.getMessage(), List.of(), null);
        }

        StringBuilder schemaText = new StringBuilder();
        for (TableInfo t : tables) {
            schemaText.append("Table: ").append(t.name()).append("\n");
            schemaText.append("  Columns:\n");
            for (ColumnInfo c : t.columns()) {
                schemaText.append("    - ").append(c.name()).append(" (").append(c.type());
                if (c.size() > 0) schemaText.append("(").append(c.size()).append(")");
                schemaText.append(c.nullable() ? ", nullable" : ", not null").append(")\n");
            }
            if (!t.pkColumns().isEmpty()) {
                schemaText.append("  Primary Key: ").append(String.join(", ", t.pkColumns())).append("\n");
            }
            for (ForeignKeyInfo fk : t.foreignKeys()) {
                schemaText.append("  Foreign Key: ").append(fk.column()).append(" -> ")
                        .append(fk.pkTable()).append("(").append(fk.pkColumn()).append(")\n");
            }
            schemaText.append("\n");
        }
        return new DdlHintsResult(true, null, tables, schemaText.toString());
    }

    public OntologyProposal generateFromDdl(String tenantId, String title) {
        DdlHintsResult hints = getDdlHints(tenantId);
        if (!hints.success() || hints.schemaText() == null) {
            OntologyProposal proposal = new OntologyProposal();
            proposal.setTenantId(tenantId);
            proposal.setTitle(title != null ? title : "DDL Analysis");
            proposal.setStatus("draft");
            proposal.setSource("ddl_analysis");
            proposal.setDescription("Failed to retrieve database schema: " + (hints.error() != null ? hints.error() : "unknown"));
            proposal.setProposedOwl("// Failed to retrieve schema");
            proposal.setProposedObda("// Failed to retrieve schema");
            proposalRepository.save(proposal);
            return proposal;
        }

        String description = hints.schemaText();

        if (llmAvailable) {
            return extractFromDescription(tenantId, title != null ? title : "DDL-generated Ontology", description);
        }

        // Fallback: generate simple OWL/OBDA from schema text
        OntologyProposal proposal = new OntologyProposal();
        proposal.setTenantId(tenantId);
        proposal.setTitle(title != null ? title : "DDL-generated Ontology");
        proposal.setStatus("draft");
        proposal.setSource("ddl_analysis");
        proposal.setDescription(description);

        StringBuilder owl = new StringBuilder();
        owl.append("@prefix ex: <http://example.org/ontology/> .\n");
        owl.append("@prefix owl: <http://www.w3.org/2002/07/owl#> .\n");
        owl.append("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n");
        owl.append("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n\n");

        StringBuilder obda = new StringBuilder();
        obda.append("[PrefixDeclaration]\n");
        obda.append("ex: http://example.org/ontology/\n");
        obda.append("owl: http://www.w3.org/2002/07/owl#\n");
        obda.append("rdfs: http://www.w3.org/2000/01/rdf-schema#\n");
        obda.append("xsd: http://www.w3.org/2001/XMLSchema#\n\n");

        for (TableInfo t : hints.tables()) {
            String className = toPascalCase(t.name());
            owl.append("ex:").append(className).append(" rdfs:subClassOf owl:Thing .\n\n");

            obda.append("[MappingDeclaration] @collection [[\n");
            obda.append("mappingId ").append(t.name()).append("\n");
            obda.append("target <ex:").append(className).append("/{").append(t.pkColumns().isEmpty() ? "id" : t.pkColumns().get(0)).append("}> a ex:").append(className).append(" ;\n");

            for (ColumnInfo c : t.columns()) {
                boolean isPk = t.pkColumns().contains(c.name());
                if (isPk) continue;
                String propName = toCamelCase(c.name());
                owl.append("ex:").append(propName).append(" rdfs:domain ex:").append(className).append(" ;\n");
                owl.append("    rdfs:range xsd:string .\n\n");
                obda.append("    ex:").append(propName).append(" {").append(c.name()).append("} ;\n");
            }
            obda.delete(obda.length() - 3, obda.length());
            obda.append("\n");

            obda.append("source SELECT ");
            List<String> cols = new ArrayList<>();
            for (ColumnInfo c : t.columns()) cols.add(c.name());
            obda.append(String.join(", ", cols));
            obda.append(" FROM ").append(t.name()).append(" ;\n");

            obda.append("]]\n\n");
        }

        proposal.setProposedOwl(owl.toString());
        proposal.setProposedObda(obda.toString());
        proposalRepository.save(proposal);
        return proposal;
    }

    public OntologyProposal applyProposal(String tenantId, String proposalId) {
        OntologyProposal proposal = proposalRepository.findById(proposalId);
        if (proposal == null || !proposal.getTenantId().equals(tenantId)) {
            return null;
        }
        if (!"draft".equals(proposal.getStatus())) {
            throw new IllegalStateException("Proposal is already " + proposal.getStatus());
        }

        var existing = tenantContentRepository.findByTenantId(tenantId);
        String mergedOwl;
        String mergedObda;

        if (existing != null) {
            String existingOwl = existing.owlContent() != null ? existing.owlContent() : "";
            String existingObda = existing.obdaContent() != null ? existing.obdaContent() : "";
            mergedOwl = appendProposal(existingOwl, proposal.getProposedOwl());
            mergedObda = appendProposal(existingObda, proposal.getProposedObda());
        } else {
            mergedOwl = proposal.getProposedOwl();
            mergedObda = proposal.getProposedObda();
        }

        tenantContentRepository.upsert(tenantId, mergedOwl, mergedObda);

        try {
            Tenant tenant = findTenant(tenantId);
            if (tenant != null) {
                tenant.setOwlContent(mergedOwl);
                tenant.setObdaContent(mergedObda);
                engineRegistry.getOrCreate(tenant);
            }
        } catch (Exception e) {
            log.warn("Engine reinit after proposal apply failed for [{}]: {}", tenantId, e.getMessage());
        }

        proposal.setStatus("applied");
        proposalRepository.save(proposal);
        return proposal;
    }

    public OntologyProposal rejectProposal(String tenantId, String proposalId, String reason) {
        OntologyProposal proposal = proposalRepository.findById(proposalId);
        if (proposal == null || !proposal.getTenantId().equals(tenantId)) {
            return null;
        }
        proposal.setStatus("rejected");
        proposal.setRejectionReason(reason);
        proposalRepository.save(proposal);
        return proposal;
    }

    public List<OntologyProposal> listProposals(String tenantId) {
        return proposalRepository.findByTenantId(tenantId);
    }

    public OntologyProposal getProposal(String tenantId, String proposalId) {
        OntologyProposal p = proposalRepository.findById(proposalId);
        if (p != null && p.getTenantId().equals(tenantId)) {
            return p;
        }
        return null;
    }

    public boolean deleteProposal(String tenantId, String proposalId) {
        OntologyProposal p = proposalRepository.findById(proposalId);
        if (p == null || !p.getTenantId().equals(tenantId)) {
            return false;
        }
        proposalRepository.deleteById(proposalId);
        return true;
    }

    private Tenant findTenant(String tenantId) {
        return tenantConfig.getTenants().stream()
                .filter(t -> t.getId().equals(tenantId))
                .findFirst()
                .orElse(null);
    }

    private LlmResult parseLlmResponse(String text) {
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int start = cleaned.indexOf('\n');
            int end = cleaned.lastIndexOf("```");
            if (end > start) {
                cleaned = cleaned.substring(start, end).trim();
            }
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, String> map = mapper.readValue(cleaned, Map.class);
            return new LlmResult(
                    map.getOrDefault("owl", "// No OWL generated"),
                    map.getOrDefault("obda", "// No OBDA generated")
            );
        } catch (Exception e) {
            log.warn("Failed to parse LLM response as JSON: {}", e.getMessage());
            return new LlmResult(cleaned, cleaned);
        }
    }

    private String generateFallbackOwl(String title, String description) {
        return "@prefix ex: <http://example.org/ontology/> .\n"
                + "@prefix owl: <http://www.w3.org/2002/07/owl#> .\n"
                + "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n\n"
                + "// Fallback OWL for: " + title + "\n"
                + "// Description: " + description + "\n"
                + "// No LLM available — define ontology manually\n";
    }

    private String generateFallbackObda() {
        return "[PrefixDeclaration]\n"
                + "ex: http://example.org/ontology/\n"
                + "owl: http://www.w3.org/2002/07/owl#\n"
                + "rdfs: http://www.w3.org/2000/01/rdf-schema#\n\n"
                + "// No OBDA mapping defined\n";
    }

    private String appendProposal(String existingContent, String proposedContent) {
        if (existingContent == null || existingContent.isBlank()) {
            return proposedContent != null ? proposedContent : "";
        }
        if (proposedContent == null || proposedContent.isBlank()) {
            return existingContent;
        }
        return existingContent.trim() + "\n\n// --- Generated by Ontology Assist ---\n\n" + proposedContent.trim();
    }

    private static String toPascalCase(String name) {
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (char c : name.toCharArray()) {
            if (c == '_' || c == ' ') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static String toCamelCase(String name) {
        String pascal = toPascalCase(name);
        if (pascal.isEmpty()) return "";
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    public record LlmResult(String owl, String obda) {}
    public record DdlHintsResult(boolean success, String error, List<TableInfo> tables, String schemaText) {}
    public record TableInfo(String name, String schema, List<ColumnInfo> columns, List<String> pkColumns, List<ForeignKeyInfo> foreignKeys) {}
    public record ColumnInfo(String name, String type, int size, boolean nullable) {}
    public record ForeignKeyInfo(String column, String pkTable, String pkColumn) {}
}
