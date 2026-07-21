package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.zhengyan.ontology.platform.config.OwlGenerationProperties;
import org.zhengyan.ontology.platform.exception.OwlGenerationException;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.repository.TenantContentRepository;

import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import org.zhengyan.ontology.platform.config.OwlGenerationProperties.ColumnOverride;
import org.zhengyan.ontology.platform.config.OwlGenerationProperties.TableOverride;

@Service
public class OwlGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(OwlGeneratorService.class);

    private static final String RDFS_LABEL = "    rdfs:label \"";

    private final OwlGenerationProperties namingProperties;
    private final JdbcMetadataReader metadataReader;
    private final TenantContentRepository tenantContentRepository;

    public OwlGeneratorService(OwlGenerationProperties namingProperties, JdbcMetadataReader metadataReader,
                               TenantContentRepository tenantContentRepository) {
        this.namingProperties = namingProperties;
        this.metadataReader = metadataReader;
        this.tenantContentRepository = tenantContentRepository;
    }

    private String resolveClassName(String tableName) {
        TableOverride override = namingProperties.getTableOverrides().get(tableName);
        if (override != null && override.className() != null && !override.className().isBlank()) {
            return override.className();
        }
        return NamingUtils.toClassName(tableName, namingProperties);
    }

    private String resolvePropertyName(String columnName, String tableName) {
        TableOverride override = namingProperties.getTableOverrides().get(tableName);
        if (override != null) {
            ColumnOverride colOverride = override.columnOverrides().get(columnName);
            if (colOverride != null && colOverride.propertyName() != null && !colOverride.propertyName().isBlank()) {
                return colOverride.propertyName();
            }
        }
        return NamingUtils.toPropertyName(columnName, tableName, namingProperties);
    }

    private boolean shouldExposeColumn(String columnName, String tableName) {
        TableOverride override = namingProperties.getTableOverrides().get(tableName);
        if (override != null) {
            ColumnOverride colOverride = override.columnOverrides().get(columnName);
            if (colOverride != null) return colOverride.expose();
        }
        return true;
    }

    private boolean shouldExposeTable(String tableName) {
        TableOverride override = namingProperties.getTableOverrides().get(tableName);
        if (override != null) return override.expose();
        return true;
    }

    public String generateOwl(Tenant tenant) {
        try (Connection conn = DriverManager.getConnection(
                tenant.getJdbcUrl(), tenant.getJdbcUsername(), tenant.getJdbcPassword())) {

            String ns = "http://ontology.zhengyan.org/ontology/" + tenant.getId() + "#";
            StringWriter sw = new StringWriter();

            sw.append("@prefix owl: <http://www.w3.org/2002/07/owl#> .\n");
            sw.append("@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n");
            sw.append("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n");
            sw.append("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n");
            sw.append("@base <").append(ns).append("> .\n\n");
            sw.append("<").append(ns).append("> rdf:type owl:Ontology .\n\n");

            List<JdbcMetadataReader.TableInfo> tables = metadataReader.readTables(conn, tenant);
            Set<String> primaryKeys = metadataReader.readPrimaryKeys(conn, tenant);

            for (JdbcMetadataReader.TableInfo table : tables) {
                if (!shouldExposeTable(table.name)) continue;
                String className = resolveClassName(table.name);
                sw.append("### ").append(table.name).append("\n");
                sw.append(":").append(className).append(" rdf:type owl:Class ;\n");
                sw.append(RDFS_LABEL).append(table.name).append("\" ;\n");
                if (table.comment != null) {
                    sw.append("    rdfs:comment \"").append(table.comment).append("\" ;\n");
                }
                sw.append("    rdfs:subClassOf owl:Thing .\n\n");
            }

            for (JdbcMetadataReader.TableInfo table : tables) {
                if (!shouldExposeTable(table.name)) continue;
                String tableClassName = resolveClassName(table.name);
                for (JdbcMetadataReader.ColumnInfo col : table.columns) {
                    if (!shouldExposeColumn(col.name, table.name)) continue;
                    String propName = resolvePropertyName(col.name, table.name);

                    String pkKey = table.name + "." + col.name;
                    boolean isPK = primaryKeys.contains(pkKey);

                    if (col.fkTargetTable != null) {
                        String targetClass = resolveClassName(col.fkTargetTable);
                        sw.append(":").append(propName).append(" rdf:type owl:ObjectProperty ;\n");
                        sw.append("    rdfs:domain :").append(tableClassName).append(" ;\n");
                        sw.append("    rdfs:range :").append(targetClass).append(" ;\n");
                        sw.append(RDFS_LABEL).append(col.name).append("\" .\n");
                        if (isPK) {
                            sw.append(":").append(propName).append(" rdf:type owl:FunctionalProperty .\n");
                        }
                        sw.append("\n");
                    } else {
                        String xsdType = NamingUtils.mapSqlTypeToXsd(col.sqlType);
                        sw.append(":").append(propName).append(" rdf:type owl:DatatypeProperty ;\n");
                        sw.append("    rdfs:domain :").append(tableClassName).append(" ;\n");
                        sw.append("    rdfs:range ").append(xsdType).append(" ;\n");
                        sw.append(RDFS_LABEL).append(col.name).append("\" .\n");
                        if (isPK) {
                            sw.append(":").append(propName).append(" rdf:type owl:FunctionalProperty .\n");
                        }
                        sw.append("\n");
                    }
                }
            }

            sw.append("### User-defined axioms\n");
            appendUserAxioms(sw, tenant.getId(), ns);

            return sw.toString();
        } catch (SQLException e) {
            throw new OwlGenerationException("Failed to generate OWL for tenant: " + tenant.getId(), e);
        }
    }

    private void appendUserAxioms(StringWriter sw, String tenantId, String ns) {
        try {
            TenantContentRepository.TenantContent content = tenantContentRepository.findByTenantId(tenantId);
            if (content == null || content.axiomConfig() == null || content.axiomConfig().isBlank()) return;

            JsonNode root = new ObjectMapper().readTree(content.axiomConfig());
            JsonNode subClassOf = root.get("subClassOf");
            if (subClassOf != null && subClassOf.isArray()) {
                for (JsonNode entry : subClassOf) {
                    String child = entry.has("child") ? entry.get("child").asText() : null;
                    String parent = entry.has("parent") ? entry.get("parent").asText() : null;
                    if (child != null && parent != null) {
                        sw.append(":").append(child).append(" rdfs:subClassOf :").append(parent).append(" .\n");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to append user axioms for tenant [{}]: {}", tenantId, e.getMessage());
        }
    }
}
