package org.zhengyan.ontology.platform.service;

import org.springframework.http.MediaType;

import java.util.Optional;

/**
 * @author 郑炎 Zheng Yan
 */
public enum SparqlResultFormat {

    JSON(MediaType.APPLICATION_JSON),          // JSON format
    SPARQL_JSON(MediaType.valueOf("application/sparql-results+json")), // JSON format
    SPARQL_XML(MediaType.valueOf("application/sparql-results+xml")),   // XML format
    CSV(MediaType.valueOf("text/csv")),        // CSV format
    TSV(MediaType.valueOf("text/tab-separated-values")), // TSV format
    TURTLE(MediaType.valueOf("text/turtle")),  // Turtle format
    RDF_XML(MediaType.valueOf("application/rdf+xml")),  // RDF/XML format
    JSON_LD(MediaType.valueOf("application/ld+json"));  // JSON-LD format

    private final MediaType mediaType;

    SparqlResultFormat(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public static Optional<SparqlResultFormat> fromAccept(String acceptHeader) {
        if (acceptHeader == null || acceptHeader.isBlank()) {
            return Optional.of(JSON);
        }
        for (SparqlResultFormat format : values()) {
            if (format.mediaType.toString().equals(acceptHeader)
                    || format.mediaType.isCompatibleWith(MediaType.parseMediaType(acceptHeader))) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }

    public boolean isGraphFormat() {
        return this == TURTLE || this == RDF_XML || this == JSON_LD;
    }
}
