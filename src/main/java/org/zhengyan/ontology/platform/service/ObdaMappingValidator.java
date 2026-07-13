package org.zhengyan.ontology.platform.service;

import org.springframework.stereotype.Component;
import org.zhengyan.ontology.platform.model.Tenant;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ObdaMappingValidator {

    private static final Pattern MAPPING_BLOCK = Pattern.compile(
            "\\[MappingDeclaration\\] @collection \\[\\[(.*?)\\]\\]",
            Pattern.DOTALL);

    private static final Pattern TARGET_IRI = Pattern.compile(
            "target\\s+(.+?)\\s*\\.");

    private static final Pattern COLUMN_PLACEHOLDER = Pattern.compile(
            "\\{(\\w+)\\}");

    private static final Pattern SOURCE_SQL = Pattern.compile(
            "source\\s+(SELECT\\s+.+?)(?=\\]\\]|\\n\\[|\\z)", Pattern.DOTALL);

    private static final Pattern FROM_TABLE = Pattern.compile(
            "FROM\\s+\"(\\w+)\"", Pattern.CASE_INSENSITIVE);

    private static final Pattern SELECT_COLUMN = Pattern.compile(
            "\"(\\w+)\"", Pattern.CASE_INSENSITIVE);

    public record ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of(), List.of());
        }

        public static ValidationResult withErrors(List<String> errors) {
            return new ValidationResult(false, errors, List.of());
        }

        public static ValidationResult withWarnings(List<String> warnings) {
            return new ValidationResult(true, List.of(), warnings);
        }
    }

    public ValidationResult validate(Tenant tenant, String obdaContent) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (obdaContent == null || obdaContent.isBlank()) {
            return ValidationResult.withErrors(List.of("OBDA content is empty"));
        }

        Matcher blockMatcher = MAPPING_BLOCK.matcher(obdaContent);
        if (!blockMatcher.find()) {
            return ValidationResult.withErrors(List.of("No mapping blocks found in OBDA content"));
        }
        blockMatcher.reset();

        Set<String> referencedTables = new LinkedHashSet<>();
        Map<String, Set<String>> tableColumns = new LinkedHashMap<>();

        while (blockMatcher.find()) {
            String block = blockMatcher.group(1);

            String mappingId = extractMappingId(block);
            if (mappingId == null) {
                warnings.add("Mapping block without mappingId");
                continue;
            }

            Matcher targetMatcher = TARGET_IRI.matcher(block);
            if (!targetMatcher.find()) {
                errors.add("Mapping '" + mappingId + "': missing target IRI");
                continue;
            }
            String target = targetMatcher.group(1);

            Set<String> targetColumns = new HashSet<>();
            Matcher colMatcher = COLUMN_PLACEHOLDER.matcher(target);
            while (colMatcher.find()) {
                targetColumns.add(colMatcher.group(1));
            }

            Matcher sourceMatcher = SOURCE_SQL.matcher(block);
            if (!sourceMatcher.find()) {
                errors.add("Mapping '" + mappingId + "': missing source SQL");
                continue;
            }
            String sourceSql = sourceMatcher.group(1);

            Matcher tableMatcher = FROM_TABLE.matcher(sourceSql);
            if (!tableMatcher.find()) {
                errors.add("Mapping '" + mappingId + "': cannot find table in source SQL");
                continue;
            }
            String tableName = tableMatcher.group(1);
            referencedTables.add(tableName);

            String fromClause = sourceSql.substring(tableMatcher.start());
            String selectPart = sourceSql.substring(0, tableMatcher.start());

            Set<String> sourceColumns = new HashSet<>();
            Matcher colNameMatcher = SELECT_COLUMN.matcher(selectPart);
            while (colNameMatcher.find()) {
                sourceColumns.add(colNameMatcher.group(1));
            }

            tableColumns.computeIfAbsent(tableName, k -> new LinkedHashSet<>()).addAll(sourceColumns);

            for (String tc : targetColumns) {
                if (!sourceColumns.contains(tc)) {
                    warnings.add("Mapping '" + mappingId + "': target IRI references column '" + tc
                            + "' not found in source SELECT for table '" + tableName + "'");
                }
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.withErrors(errors);
        }

        List<String> dbErrors = validateAgainstDatabase(tenant, referencedTables, tableColumns);
        if (!dbErrors.isEmpty()) {
            return ValidationResult.withErrors(dbErrors);
        }

        return new ValidationResult(true, errors, warnings);
    }

    private String extractMappingId(String block) {
        Pattern p = Pattern.compile("mappingId\\s+(\\S+)");
        Matcher m = p.matcher(block);
        return m.find() ? m.group(1) : null;
    }

    private List<String> validateAgainstDatabase(Tenant tenant, Set<String> tables,
                                                  Map<String, Set<String>> tableColumns) {
        List<String> errors = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(
                tenant.getJdbcUrl(), tenant.getJdbcUsername(), tenant.getJdbcPassword())) {
            String schema = getSchema(tenant);

            for (String table : tables) {
                boolean tableFound = false;
                Set<String> dbColumns = new HashSet<>();
                try (ResultSet rs = conn.getMetaData().getTables(null, schema, table, new String[]{"TABLE"})) {
                    tableFound = rs.next();
                }
                if (!tableFound) {
                    errors.add("Table '" + table + "' does not exist in the database");
                    continue;
                }
                try (ResultSet rs = conn.getMetaData().getColumns(null, schema, table, "%")) {
                    while (rs.next()) {
                        dbColumns.add(rs.getString("COLUMN_NAME"));
                    }
                }
                Set<String> referenced = tableColumns.getOrDefault(table, Set.of());
                for (String col : referenced) {
                    if (!dbColumns.contains(col)) {
                        errors.add("Column '" + col + "' does not exist in table '" + table + "'");
                    }
                }
            }
        } catch (SQLException e) {
            errors.add("Database connection error: " + e.getMessage());
        }
        return errors;
    }

    private String getSchema(Tenant tenant) {
        String url = tenant.getJdbcUrl().toLowerCase();
        if (url.contains("h2")) return "PUBLIC";
        if (url.contains("mysql")) return null;
        if (url.contains("postgresql")) return "public";
        if (url.contains("sqlserver")) return "dbo";
        return null;
    }
}
