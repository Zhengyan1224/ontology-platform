package org.zhengyan.ontology.platform.service;

import org.zhengyan.ontology.platform.config.OwlGenerationProperties;

import java.sql.Types;

public final class NamingUtils {

    private NamingUtils() {}

    public static String toClassName(String name, OwlGenerationProperties props) {
        String singular = singularize(name);
        String base = toPascalCase(singular);
        String prefix = props.getTableToClassPrefix();
        String className = prefix.isEmpty() ? base : toPascalCase(prefix) + base;
        if ("camelCase".equalsIgnoreCase(props.getNameCase())) {
            if (!className.isEmpty()) {
                className = Character.toLowerCase(className.charAt(0)) + className.substring(1);
            }
        }
        return className;
    }

    public static String toPropertyName(String columnName, String tableName, OwlGenerationProperties props) {
        String base = columnName.toLowerCase()
                .replace(tableName.toLowerCase() + "_id", tableName.toLowerCase() + "Id")
                .replace("_id", "Id")
                .replace("_", "");
        String prefix = props.getColumnToPropertyPrefix();
        if (!prefix.isEmpty()) {
            base = prefix + Character.toUpperCase(base.charAt(0)) + base.substring(1);
        }
        return base;
    }

    public static String toIriPrefix(String className) {
        if (className == null || className.isEmpty()) return "";
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    public static String toIriTemplate(String pkColumn, String iriTemplatePattern) {
        return iriTemplatePattern.replace("{pk}", "{" + pkColumn + "}");
    }

    public static String singularize(String name) {
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

    public static String toPascalCase(String name) {
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

    public static String mapSqlTypeToXsd(int sqlType) {
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

    public static boolean isJoinTable(JdbcMetadataReader.TableInfo table) {
        if (table.columns.isEmpty()) return false;
        return table.columns.stream().allMatch(c -> c.fkTargetTable != null);
    }
}
