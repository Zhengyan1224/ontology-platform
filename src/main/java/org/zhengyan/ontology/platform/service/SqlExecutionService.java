package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.model.Tenant;

import java.sql.*;
import java.util.*;

@Service
public class SqlExecutionService {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutionService.class);

    public SqlResult execute(Tenant tenant, String sql) {
        String trimmed = sql.trim();
        String upper = trimmed.toUpperCase();
        long start = System.currentTimeMillis();
        boolean isQuery = upper.startsWith("SELECT") || upper.startsWith("WITH")
                || upper.startsWith("EXPLAIN") || upper.startsWith("SHOW")
                || upper.startsWith("DESCRIBE");

        try (Connection conn = DriverManager.getConnection(
                tenant.getJdbcUrl(), tenant.getJdbcUsername(), tenant.getJdbcPassword())) {

            if (isQuery) {
                return executeQuery(conn, trimmed, start);
            } else {
                return executeUpdate(conn, trimmed, start);
            }
        } catch (SQLException e) {
            long elapsed = System.currentTimeMillis() - start;
            return new SqlResult(false, List.of(), e.getMessage(), 0, elapsed, List.of());
        }
    }

    private SqlResult executeQuery(Connection conn, String sql, long start) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(meta.getColumnLabel(i));
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    Object val = rs.getObject(i);
                    row.put(meta.getColumnLabel(i), val != null ? val.toString() : null);
                }
                rows.add(row);
            }

            long elapsed = System.currentTimeMillis() - start;
            return new SqlResult(true, columns, null, rows.size(), elapsed, rows);
        }
    }

    private SqlResult executeUpdate(Connection conn, String sql, long start) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            int affected = stmt.executeUpdate(sql);
            long elapsed = System.currentTimeMillis() - start;
            return new SqlResult(true, List.of("affected_rows"), null, affected, elapsed, List.of());
        }
    }

    public record SqlResult(boolean success, List<String> columns, String error, int rowCount, long executionTimeMs, List<Map<String, Object>> rows) {}
}
