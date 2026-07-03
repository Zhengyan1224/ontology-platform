package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@Component
public class ObdaMappingParser {

    private static final Logger log = LoggerFactory.getLogger(ObdaMappingParser.class);
    private static final String SOURCE = "source";

    public ObdaSchema parse(String obdaPath) {
        ObdaSchema schema = new ObdaSchema();

        File obdaFile = resolveFile(obdaPath);
        if (!obdaFile.exists()) {
            log.warn("OBDA file not found: {}", obdaFile.getAbsolutePath());
            return schema;
        }

        try {
            List<String> lines = Files.readAllLines(obdaFile.toPath());
            parseLines(lines, schema);
            log.info("Parsed OBDA: {} mappings, {} prefixes", schema.mappings.size(), schema.prefixes.size());
        } catch (IOException e) {
            log.error("Failed to parse OBDA file: {}", obdaPath, e);
        }

        return schema;
    }

    private void parseLines(List<String> lines, ObdaSchema schema) {
        boolean inMapping = false;
        String currentMappingId = null;
        String currentTarget = null;
        StringBuilder currentSource = new StringBuilder();
        boolean hasCurrentMapping = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("[PrefixDeclaration]")) {
                inMapping = false;
            } else if (trimmed.startsWith("[MappingDeclaration]")) {
                inMapping = true;
            } else if (inMapping) {
                if (trimmed.startsWith("mappingId")) {
                    if (hasCurrentMapping) {
                        schema.mappings.add(createMapping(currentMappingId, currentTarget, currentSource.toString()));
                    }
                    currentMappingId = extractValue(trimmed);
                    currentSource = new StringBuilder();
                    hasCurrentMapping = true;
                } else if (trimmed.startsWith("target")) {
                    currentTarget = extractValue(trimmed);
                } else if (trimmed.startsWith(SOURCE)) {
                    currentSource = new StringBuilder(extractValue(trimmed));
                } else if (!trimmed.isEmpty() && !trimmed.equals("]]") && currentSource.length() > 0 && !trimmed.startsWith(SOURCE)) {
                    currentSource.append(" ").append(trimmed);
                }
            } else {
                if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains(":")) {
                    String[] parts = trimmed.split("\\s+", 2);
                    if (parts.length == 2) {
                        schema.prefixes.put(parts[0], parts[1].replace("<", "").replace(">", ""));
                    }
                }
            }
        }

        if (hasCurrentMapping) {
            schema.mappings.add(createMapping(currentMappingId, currentTarget, currentSource.toString()));
        }
    }

    private Map<String, Object> createMapping(String id, String target, String source) {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("mappingId", id);
        mapping.put("target", target);
        mapping.put(SOURCE, source);

        String sourceTable = extractTableName(source);
        if (sourceTable != null) {
            mapping.put("sourceTable", sourceTable);
        }

        return mapping;
    }

    private String extractTableName(String sql) {
        if (sql == null || sql.isBlank()) return null;
        String upper = sql.toUpperCase().trim();
        int fromIdx = upper.indexOf("FROM ");
        if (fromIdx < 0) return null;
        String afterFrom = sql.substring(fromIdx + 5).trim();
        if (afterFrom.startsWith("\"")) {
            return afterFrom.substring(1, afterFrom.indexOf("\"", 1));
        }
        String[] parts = afterFrom.split("[\\s,;)]");
        return parts.length > 0 ? parts[0] : null;
    }

    private File resolveFile(String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File("src/main/resources", path);
        }
        return file;
    }

    private String extractValue(String line) {
        int idx = line.indexOf('\t');
        if (idx < 0) idx = line.indexOf(' ');
        if (idx < 0) return "";
        return line.substring(idx + 1).trim();
    }

    public static class ObdaSchema {
        public final Map<String, String> prefixes = new LinkedHashMap<>();
        public final List<Map<String, Object>> mappings = new ArrayList<>();
    }
}
