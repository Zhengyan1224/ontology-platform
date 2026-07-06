package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.config.OwlGenerationProperties;
import org.zhengyan.ontology.platform.model.Tenant;

import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

@Service
public class OwlGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(OwlGeneratorService.class);

    private static final String RDFS_LABEL = "    rdfs:label \"";

    private final OwlGenerationProperties namingProperties;
    private final JdbcMetadataReader metadataReader;

    public OwlGeneratorService(OwlGenerationProperties namingProperties, JdbcMetadataReader metadataReader) {
        this.namingProperties = namingProperties;
        this.metadataReader = metadataReader;
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

            List<JdbcMetadataReader.TableInfo> tables = metadataReader.readTables(conn, tenant);
            Set<String> primaryKeys = metadataReader.readPrimaryKeys(conn, tenant);

            for (JdbcMetadataReader.TableInfo table : tables) {
                String className = NamingUtils.toClassName(table.name, namingProperties);
                sw.append("### ").append(table.name).append("\n");
                sw.append(":").append(className).append(" rdf:type owl:Class ;\n");
                sw.append(RDFS_LABEL).append(table.name).append("\" ;\n");
                if (table.comment != null) {
                    sw.append("    rdfs:comment \"").append(table.comment).append("\" ;\n");
                }
                sw.append("    rdfs:subClassOf owl:Thing .\n\n");
            }

            for (JdbcMetadataReader.TableInfo table : tables) {
                for (JdbcMetadataReader.ColumnInfo col : table.columns) {
                    String propName = NamingUtils.toPropertyName(col.name, table.name, namingProperties);

                    String pkKey = table.name + "." + col.name;
                    boolean isPK = primaryKeys.contains(pkKey);

                    if (col.fkTargetTable != null) {
                        String targetClass = NamingUtils.toClassName(col.fkTargetTable, namingProperties);
                        sw.append(":").append(propName).append(" rdf:type owl:ObjectProperty ;\n");
                        sw.append("    rdfs:domain :").append(NamingUtils.toClassName(table.name, namingProperties)).append(" ;\n");
                        sw.append("    rdfs:range :").append(targetClass).append(" ;\n");
                        sw.append(RDFS_LABEL).append(col.name).append("\" .\n");
                        if (isPK) {
                            sw.append(":").append(propName).append(" rdf:type owl:FunctionalProperty .\n");
                        }
                        sw.append("\n");
                    } else {
                        String xsdType = NamingUtils.mapSqlTypeToXsd(col.sqlType);
                        sw.append(":").append(propName).append(" rdf:type owl:DatatypeProperty ;\n");
                        sw.append("    rdfs:domain :").append(NamingUtils.toClassName(table.name, namingProperties)).append(" ;\n");
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
}
