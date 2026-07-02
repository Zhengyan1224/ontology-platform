package org.zhengyan.ontology.platform.service;

import org.springframework.http.MediaType;

import java.util.Optional;

public enum SparqlResultFormat {

    JSON(MediaType.APPLICATION_JSON),
    SPARQL_JSON(MediaType.valueOf("application/sparql-results+json")),
    SPARQL_XML(MediaType.valueOf("application/sparql-results+xml")),
    CSV(MediaType.valueOf("text/csv")),
    TSV(MediaType.valueOf("text/tab-separated-values")),
    TURTLE(MediaType.valueOf("text/turtle")),
    RDF_XML(MediaType.valueOf("application/rdf+xml")),
    JSON_LD(MediaType.valueOf("application/ld+json"));

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
