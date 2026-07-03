package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.config.OwlGenerationProperties;
import org.zhengyan.ontology.platform.model.Tenant;

import java.io.StringWriter;
import java.sql.*;
import java.util.*;

@Service
public class OwlGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(OwlGeneratorService.class);

    private static final String RDFS_LABEL = "    rdfs:label \"";

    private final OwlGenerationProperties namingProperties;

    public OwlGeneratorService(OwlGenerationProperties namingProperties) {
        this.namingProperties = namingProperties;
    }

    public String generateOwl(Tenant tenant) throws Exception {
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

            List<TableInfo> tables = readTables(conn, tenant);
            Set<String> primaryKeys = readPrimaryKeys(conn, tenant);
            Set<String> objectProperties = new HashSet<>();

            for (TableInfo table : tables) {
                String className = toClassName(table.name);
                sw.append("### ").append(table.name).append("\n");
                sw.append(":").append(className).append(" rdf:type owl:Class ;\n");
                sw.append(RDFS_LABEL).append(table.name).append("\" ;\n");
                if (table.comment != null) {
                    sw.append("    rdfs:comment \"").append(table.comment).append("\" ;\n");
                }
                sw.append("    rdfs:subClassOf owl:Thing .\n\n");

                for (ColumnInfo col : table.columns) {
                    if (col.fkTargetTable != null) {
                        objectProperties.add(col.name);
                    }
                }
            }

            for (TableInfo table : tables) {
                for (ColumnInfo col : table.columns) {
                    String propName = toPropertyName(col.name, table.name);

                    String pkKey = table.name + "." + col.name;
                    boolean isPK = primaryKeys.contains(pkKey);

                    if (col.fkTargetTable != null) {
                        String targetClass = toClassName(col.fkTargetTable);
                        sw.append(":").append(propName).append(" rdf:type owl:ObjectProperty ;\n");
                        sw.append("    rdfs:domain :").append(toClassName(table.name)).append(" ;\n");
                        sw.append("    rdfs:range :").append(targetClass).append(" ;\n");
                        sw.append(RDFS_LABEL).append(col.name).append("\" .\n");
                        if (isPK) {
                            sw.append(":").append(propName).append(" rdf:type owl:FunctionalProperty .\n");
                        }
                        sw.append("\n");
                    } else {
                        String xsdType = mapSqlTypeToXsd(col.sqlType);
                        sw.append(":").append(propName).append(" rdf:type owl:DatatypeProperty ;\n");
                        sw.append("    rdfs:domain :").append(toClassName(table.name)).append(" ;\n");
                        sw.append("    rdfs:range ").append(xsdType).append(" ;\n");
                        sw.append(RDFS_LABEL).append(col.name).append("\" .\n");
                        if (isPK) {
                            sw.append(":").append(propName).append(" rdf:type owl:FunctionalProperty .\n");
                        }
                        sw.append("\n");
                    }
                }
            }

            return sw.toString();
        }
    }

    private List<TableInfo> readTables(Connection conn, Tenant tenant) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();
        String catalog = conn.getCatalog();
        String schema = getSchema(tenant);

        try (ResultSet rs = conn.getMetaData().getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                TableInfo table = new TableInfo();
                table.name = rs.getString("TABLE_NAME");
                table.comment = getTableComment(conn, schema, table.name);
                table.columns = readColumns(conn, catalog, schema, table.name);
                readForeignKeys(conn, catalog, schema, table);
                tables.add(table);
            }
        }
        return tables;
    }

    private List<ColumnInfo> readColumns(Connection conn, String catalog, String schema, String tableName) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getColumns(catalog, schema, tableName, "%")) {
            while (rs.next()) {
                ColumnInfo col = new ColumnInfo();
                col.name = rs.getString("COLUMN_NAME");
                col.sqlType = rs.getInt("DATA_TYPE");
                col.isNullable = "YES".equals(rs.getString("IS_NULLABLE"));
                col.comment = rs.getString("REMARKS");
                columns.add(col);
            }
        }
        return columns;
    }

    private Set<String> readPrimaryKeys(Connection conn, Tenant tenant) throws SQLException {
        Set<String> pks = new HashSet<>();
        String catalog = conn.getCatalog();
        String schema = getSchema(tenant);
        try (ResultSet rs = conn.getMetaData().getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                try (ResultSet pkRs = conn.getMetaData().getPrimaryKeys(catalog, schema, tableName)) {
                    while (pkRs.next()) {
                        pks.add(pkRs.getString("TABLE_NAME") + "." + pkRs.getString("COLUMN_NAME"));
                    }
                }
            }
        }
        return pks;
    }

    private void readForeignKeys(Connection conn, String catalog, String schema, TableInfo table) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getImportedKeys(catalog, schema, table.name)) {
            while (rs.next()) {
                String fkCol = rs.getString("FKCOLUMN_NAME");
                String pkTable = rs.getString("PKTABLE_NAME");
                for (ColumnInfo col : table.columns) {
                    if (col.name.equals(fkCol)) {
                        col.fkTargetTable = pkTable;
                    }
                }
            }
        }
    }

    private String getTableComment(Connection conn, String schema, String tableName) {
        String sql = "SELECT REMARKS FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema != null ? schema : "PUBLIC");
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (Exception e) {
            log.debug("Could not read comment for table {}: {}", tableName, e.getMessage());
        }
        return null;
    }

    private String getSchema(Tenant tenant) {
        String url = tenant.getJdbcUrl().toLowerCase();
        if (url.contains("h2")) return "PUBLIC";
        if (url.contains("mysql")) return null;
        if (url.contains("postgresql")) return "public";
        if (url.contains("sqlserver")) return "dbo";
        return null;
    }

    private String toClassName(String name) {
        String singular = singularize(name);
        String base = toPascalCase(singular);
        String prefix = namingProperties.getTableToClassPrefix();
        String className = prefix.isEmpty() ? base : toPascalCase(prefix) + base;
        if ("camelCase".equalsIgnoreCase(namingProperties.getNameCase())) {
            if (className.length() > 0) {
                className = Character.toLowerCase(className.charAt(0)) + className.substring(1);
            }
        }
        return className;
    }

    private String toPropertyName(String columnName, String tableName) {
        String base = columnName.toLowerCase()
                .replace(tableName.toLowerCase() + "_id", tableName.toLowerCase() + "Id")
                .replace("_id", "Id")
                .replace("_", "");
        String prefix = namingProperties.getColumnToPropertyPrefix();
        if (!prefix.isEmpty()) {
            base = prefix + Character.toUpperCase(base.charAt(0)) + base.substring(1);
        }
        return base;
    }

    private String singularize(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith("ies") && lower.length() > 3) {
            return name.substring(0, name.length() - 3) + "y";
        }
        if (lower.endsWith("sses") && lower.length() > 4) {
            return name.substring(0, name.length() - 2);
        }
        if (lower.endsWith("ses") && lower.length() > 3) {
            return name.substring(0, name.length() - 2);
        }
        if (lower.endsWith("s") && !lower.endsWith("ss") && lower.length() > 2) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }

    private String toPascalCase(String name) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : name.toCharArray()) {
            if (c == '_' || c == ' ') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private String mapSqlTypeToXsd(int sqlType) {
        return switch (sqlType) {
            case Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> "xsd:integer";
            case Types.DECIMAL, Types.NUMERIC, Types.FLOAT, Types.DOUBLE, Types.REAL -> "xsd:decimal";
            case Types.BOOLEAN, Types.BIT -> "xsd:boolean";
            case Types.DATE -> "xsd:date";
            case Types.TIMESTAMP -> "xsd:dateTime";
            case Types.TIME -> "xsd:time";
            default -> "xsd:string";
        };
    }

    private static class TableInfo {
        String name;
        String comment;
        List<ColumnInfo> columns;
    }

    private static class ColumnInfo {
        String name;
        int sqlType;
        boolean isNullable;
        String comment;
        String fkTargetTable;
    }
}
