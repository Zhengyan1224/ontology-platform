package org.zhengyan.ontology.platform.service;

import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.config.OwlGenerationProperties;
import org.zhengyan.ontology.platform.exception.ObdaGenerationException;
import org.zhengyan.ontology.platform.model.Tenant;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.zhengyan.ontology.platform.config.OwlGenerationProperties.ColumnOverride;
import org.zhengyan.ontology.platform.config.OwlGenerationProperties.TableOverride;

@Service
public class ObdaGeneratorService {

    private final OwlGenerationProperties namingProperties;
    private final JdbcMetadataReader metadataReader;

    public ObdaGeneratorService(OwlGenerationProperties namingProperties, JdbcMetadataReader metadataReader) {
        this.namingProperties = namingProperties;
        this.metadataReader = metadataReader;
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

    public String generateObda(Tenant tenant) {
        try (Connection conn = DriverManager.getConnection(
                tenant.getJdbcUrl(), tenant.getJdbcUsername(), tenant.getJdbcPassword())) {

            List<JdbcMetadataReader.TableInfo> tables = metadataReader.readTables(conn, tenant);
            Set<String> primaryKeys = metadataReader.readPrimaryKeys(conn, tenant);
            String ns = "http://ontology.zhengyan.org/ontology/" + tenant.getId() + "#";
            String iriTemplate = namingProperties.getIriTemplate();
            String joinBehavior = namingProperties.getJoinTableBehavior();

            StringBuilder sb = new StringBuilder();
            sb.append("[PrefixDeclaration]\n");
            sb.append(":\t\t").append(ns).append("\n");
            sb.append("rdf:\t\thttp://www.w3.org/1999/02/22-rdf-syntax-ns#\n");
            sb.append("xsd:\t\thttp://www.w3.org/2001/XMLSchema#\n\n");

            for (JdbcMetadataReader.TableInfo table : tables) {
                if (!shouldExposeTable(table.name)) continue;
                boolean isJoin = NamingUtils.isJoinTable(table);
                if (isJoin && "skip".equalsIgnoreCase(joinBehavior)) {
                    continue;
                }

                String className = resolveClassName(table.name);
                String iriPrefix = NamingUtils.toIriPrefix(className);

                String pkColumn = findPrimaryKeyColumn(table, primaryKeys);

                if (isJoin && "object-only".equalsIgnoreCase(joinBehavior)) {
                    for (JdbcMetadataReader.ColumnInfo col : table.columns) {
                        if (col.fkTargetTable != null) {
                            if (!shouldExposeColumn(col.name, table.name)) continue;
                            String targetClassName = resolveClassName(col.fkTargetTable);
                            String targetIriPrefix = NamingUtils.toIriPrefix(targetClassName);
                            String objPropName = resolvePropertyName(col.name, table.name);

                            sb.append("[MappingDeclaration] @collection [[\n");
                            sb.append("mappingId\top_").append(className).append("_").append(objPropName).append("\n");
                            sb.append("target\t\t:").append(iriPrefix)
                                    .append(NamingUtils.toIriTemplate(pkColumn, iriTemplate))
                                    .append(" :").append(objPropName)
                                    .append(" :").append(targetIriPrefix)
                                    .append(NamingUtils.toIriTemplate(col.name, iriTemplate)).append(" .\n");
                            sb.append("source\t\tSELECT ").append(quoteColumn(pkColumn));
                            for (JdbcMetadataReader.ColumnInfo otherCol : table.columns) {
                                if (!otherCol.name.equals(pkColumn)) {
                                    sb.append(", ").append(quoteColumn(otherCol.name));
                                }
                            }
                            sb.append(" FROM ").append(quoteColumn(table.name)).append("\n");
                            sb.append("]]\n\n");
                        }
                    }
                    continue;
                }

                sb.append("[MappingDeclaration] @collection [[\n");
                sb.append("mappingId\tcl_").append(className).append("\n");

                sb.append("target\t\t:").append(iriPrefix)
                        .append(NamingUtils.toIriTemplate(pkColumn, iriTemplate))
                        .append(" rdf:type :").append(className);

                for (JdbcMetadataReader.ColumnInfo col : table.columns) {
                    if (!shouldExposeColumn(col.name, table.name)) continue;
                    String propName = resolvePropertyName(col.name, table.name);
                    if (col.fkTargetTable != null) {
                        String targetClassName = resolveClassName(col.fkTargetTable);
                        String targetIriPrefix = NamingUtils.toIriPrefix(targetClassName);
                        String fkIri = NamingUtils.toIriTemplate(col.name, iriTemplate);
                        sb.append(" ; :").append(propName).append(" :").append(targetIriPrefix).append(fkIri);
                    } else {
                        String xsdType = NamingUtils.mapSqlTypeToXsd(col.sqlType);
                        if ("xsd:string".equals(xsdType)) {
                            sb.append(" ; :").append(propName).append(" {").append(col.name).append("}");
                        } else {
                            sb.append(" ; :").append(propName).append(" {").append(col.name).append("}^^").append(xsdType);
                        }
                    }
                }
                sb.append(" .\n");

                String selectCols = table.columns.stream()
                        .map(c -> quoteColumn(c.name))
                        .collect(Collectors.joining(", "));
                sb.append("source\t\tSELECT ").append(selectCols)
                        .append(" FROM ").append(quoteColumn(table.name)).append("\n");
                sb.append("]]\n\n");
            }

            return sb.toString();
        } catch (SQLException e) {
            throw new ObdaGenerationException("Failed to generate OBDA for tenant: " + tenant.getId(), e);
        }
    }

    private String findPrimaryKeyColumn(JdbcMetadataReader.TableInfo table, Set<String> primaryKeys) {
        for (JdbcMetadataReader.ColumnInfo col : table.columns) {
            if (primaryKeys.contains(table.name + "." + col.name)) {
                return col.name;
            }
        }
        return table.columns.isEmpty() ? "id" : table.columns.get(0).name;
    }

    private String findPrimaryKeyColumnForTable(List<JdbcMetadataReader.TableInfo> tables,
                                                 String tableName, Set<String> primaryKeys) {
        for (JdbcMetadataReader.TableInfo table : tables) {
            if (table.name.equalsIgnoreCase(tableName) || table.name.equals(tableName)) {
                for (JdbcMetadataReader.ColumnInfo col : table.columns) {
                    if (primaryKeys.contains(table.name + "." + col.name)) {
                        return col.name;
                    }
                }
                return table.columns.isEmpty() ? "id" : table.columns.get(0).name;
            }
        }
        return "id";
    }

    private String quoteColumn(String name) {
        return "\"" + name + "\"";
    }
}
