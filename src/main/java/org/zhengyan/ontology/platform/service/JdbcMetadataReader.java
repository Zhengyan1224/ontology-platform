package org.zhengyan.ontology.platform.service;

import org.springframework.stereotype.Component;
import org.zhengyan.ontology.platform.model.Tenant;

import java.sql.*;
import java.util.*;

@Component
public class JdbcMetadataReader {

    public List<TableInfo> readTables(Connection conn, Tenant tenant) throws SQLException {
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

    public Set<String> readPrimaryKeys(Connection conn, Tenant tenant) throws SQLException {
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
            // comment unavailable on this DB
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

    public static class TableInfo {
        public String name;
        public String comment;
        public List<ColumnInfo> columns;
    }

    public static class ColumnInfo {
        public String name;
        public int sqlType;
        public boolean isNullable;
        public String comment;
        public String fkTargetTable;
    }
}
