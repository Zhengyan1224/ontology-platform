package org.zhengyan.ontology.platform.service;

import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.eclipse.rdf4j.query.impl.MapBindingSet;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriter;
import org.eclipse.rdf4j.query.resultio.sparqlxml.SPARQLResultsXMLWriter;
import org.eclipse.rdf4j.query.resultio.text.csv.SPARQLResultsCSVWriter;
import org.eclipse.rdf4j.query.resultio.text.tsv.SPARQLResultsTSVWriter;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

@Component
public class SparqlResultFormatter {

    public void writeTupleResult(SparqlResultFormat format, SparqlQueryResult queryResult,
                                 OutputStream out) throws Exception {
        List<String> bindingNames = queryResult.getVariables();
        List<Map<String, Object>> rows = queryResult.getResults();

        switch (format) {
            case SPARQL_XML -> {
                SPARQLResultsXMLWriter writer = new SPARQLResultsXMLWriter(out);
                writer.startQueryResult(bindingNames);
                for (Map<String, Object> row : rows) {
                    MapBindingSet bs = new MapBindingSet();
                    for (String var : bindingNames) {
                        Object val = row.get(var);
                        if (val != null) {
                            bs.addBinding(var, SimpleValueFactory.getInstance().createLiteral(val.toString()));
                        }
                    }
                    writer.handleSolution(bs);
                }
                writer.endQueryResult();
            }
            case CSV -> {
                SPARQLResultsCSVWriter writer = new SPARQLResultsCSVWriter(out);
                writer.startQueryResult(bindingNames);
                for (Map<String, Object> row : rows) {
                    MapBindingSet bs = new MapBindingSet();
                    for (String var : bindingNames) {
                        Object val = row.get(var);
                        if (val != null) {
                            bs.addBinding(var, SimpleValueFactory.getInstance().createLiteral(val.toString()));
                        }
                    }
                    writer.handleSolution(bs);
                }
                writer.endQueryResult();
            }
            case TSV -> {
                SPARQLResultsTSVWriter writer = new SPARQLResultsTSVWriter(out);
                writer.startQueryResult(bindingNames);
                for (Map<String, Object> row : rows) {
                    MapBindingSet bs = new MapBindingSet();
                    for (String var : bindingNames) {
                        Object val = row.get(var);
                        if (val != null) {
                            bs.addBinding(var, SimpleValueFactory.getInstance().createLiteral(val.toString()));
                        }
                    }
                    writer.handleSolution(bs);
                }
                writer.endQueryResult();
            }
            default -> {
                SPARQLResultsJSONWriter writer = new SPARQLResultsJSONWriter(out);
                writer.startQueryResult(bindingNames);
                for (Map<String, Object> row : rows) {
                    MapBindingSet bs = new MapBindingSet();
                    for (String var : bindingNames) {
                        Object val = row.get(var);
                        if (val != null) {
                            bs.addBinding(var, SimpleValueFactory.getInstance().createLiteral(val.toString()));
                        }
                    }
                    writer.handleSolution(bs);
                }
                writer.endQueryResult();
            }
        }
    }

    public void writeGraphResult(SparqlResultFormat format, Model model, OutputStream out) throws Exception {
        RDFFormat rdfFormat = switch (format) {
            case TURTLE -> RDFFormat.TURTLE;
            case RDF_XML -> RDFFormat.RDFXML;
            case JSON_LD -> RDFFormat.JSONLD;
            default -> RDFFormat.TURTLE;
        };
        Rio.write(model, out, rdfFormat);
    }

    public MediaType getMediaType(SparqlResultFormat format) {
        return switch (format) {
            case JSON, SPARQL_JSON -> MediaType.valueOf("application/sparql-results+json");
            case SPARQL_XML -> MediaType.valueOf("application/sparql-results+xml");
            case CSV -> MediaType.valueOf("text/csv");
            case TSV -> MediaType.valueOf("text/tab-separated-values");
            case TURTLE -> MediaType.valueOf("text/turtle");
            case RDF_XML -> MediaType.valueOf("application/rdf+xml");
            case JSON_LD -> MediaType.valueOf("application/ld+json");
        };
    }
}
