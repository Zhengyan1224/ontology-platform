package org.zhengyan.ontology.platform.service;

import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.config.OwlGenerationProperties;
import org.zhengyan.ontology.platform.model.Tenant;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ObdaGeneratorService {

    private final OwlGenerationProperties namingProperties;
    private final JdbcMetadataReader metadataReader;

    public ObdaGeneratorService(OwlGenerationProperties namingProperties, JdbcMetadataReader metadataReader) {
        this.namingProperties = namingProperties;
        this.metadataReader = metadataReader;
    }

    public String generateObda(Tenant tenant) throws Exception {
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
            sb.append("owl:\t\t<http://www.w3.org/2002/07/owl#>\n");
            sb.append("rdf:\t\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n");
            sb.append("xsd:\t\t<http://www.w3.org/2001/XMLSchema#>\n");
            sb.append("rdfs:\t\t<http://www.w3.org/2000/01/rdf-schema#>\n\n");

            for (JdbcMetadataReader.TableInfo table : tables) {
                boolean isJoin = NamingUtils.isJoinTable(table);
                if (isJoin && "skip".equalsIgnoreCase(joinBehavior)) {
                    continue;
                }

                String className = NamingUtils.toClassName(table.name, namingProperties);
                String iriPrefix = NamingUtils.toIriPrefix(className);

                String pkColumn = findPrimaryKeyColumn(table, primaryKeys);

                if (isJoin && "object-only".equalsIgnoreCase(joinBehavior)) {
                    for (JdbcMetadataReader.ColumnInfo col : table.columns) {
                        if (col.fkTargetTable != null) {
                            String targetClassName = NamingUtils.toClassName(col.fkTargetTable, namingProperties);
                            String targetIriPrefix = NamingUtils.toIriPrefix(targetClassName);
                            String objPropName = NamingUtils.toPropertyName(col.name, table.name, namingProperties);

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
                    String propName = NamingUtils.toPropertyName(col.name, table.name, namingProperties);
                    if (col.fkTargetTable != null) {
                        String targetClassName = NamingUtils.toClassName(col.fkTargetTable, namingProperties);
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
