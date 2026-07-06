package org.zhengyan.ontology.platform.service;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;

@Component
public class OwlSchemaParser {

    private static final Logger log = LoggerFactory.getLogger(OwlSchemaParser.class);

    public OwlSchema parse(String owlPath) {
        OwlSchema schema = new OwlSchema();

        try {
            File owlFile = resolveFile(owlPath);
            if (!owlFile.exists()) {
                log.warn("OWL file not found: {}", owlFile.getAbsolutePath());
                return schema;
            }

            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLOntology ontology = manager.loadOntologyFromOntologyDocument(owlFile);
            IRI ontologyIri = ontology.getOntologyID().getOntologyIRI().orElse(null);
            Map<String, Map<String, Object>> propertiesByIri = new LinkedHashMap<>();

            for (OWLClass cls : ontology.getClassesInSignature()) {
                String iri = cls.getIRI().toString();
                schema.classes.add(Map.of("iri", iri, "name", toLocalName(iri, ontologyIri)));
            }

            for (OWLSubClassOfAxiom axiom : ontology.getAxioms(AxiomType.SUBCLASS_OF)) {
                if (axiom.getSubClass().isAnonymous() || axiom.getSuperClass().isAnonymous()) {
                    continue;
                }
                String child = axiom.getSubClass().asOWLClass().getIRI().toString();
                String parent = axiom.getSuperClass().asOWLClass().getIRI().toString();
                schema.classHierarchy.add(Map.of("child", child, "parent", parent));
            }

            for (OWLSubPropertyAxiom<?> axiom : ontology.getAxioms(AxiomType.SUB_OBJECT_PROPERTY)) {
                OWLObjectPropertyExpression sub = (OWLObjectPropertyExpression) axiom.getSubProperty();
                OWLObjectPropertyExpression sup = (OWLObjectPropertyExpression) axiom.getSuperProperty();
                if (sub.isAnonymous() || sup.isAnonymous()) {
                    continue;
                }
                schema.subPropertyOf.add(Map.of(
                        "child", sub.asOWLObjectProperty().getIRI().toString(),
                        "parent", sup.asOWLObjectProperty().getIRI().toString()));
            }

            for (OWLObjectProperty prop : ontology.getObjectPropertiesInSignature()) {
                String iri = prop.getIRI().toString();
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("iri", iri);
                p.put("name", toLocalName(iri, ontologyIri));
                p.put("type", "object");
                schema.properties.add(p);
                propertiesByIri.put(iri, p);
            }

            for (OWLDataProperty prop : ontology.getDataPropertiesInSignature()) {
                String iri = prop.getIRI().toString();
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("iri", iri);
                p.put("name", toLocalName(iri, ontologyIri));
                p.put("type", "datatype");
                schema.properties.add(p);
                propertiesByIri.put(iri, p);
            }

            for (OWLObjectPropertyDomainAxiom axiom : ontology.getAxioms(AxiomType.OBJECT_PROPERTY_DOMAIN)) {
                OWLObjectPropertyExpression property = axiom.getProperty();
                if (property.isAnonymous() || axiom.getDomain().isAnonymous()) {
                    continue;
                }
                String iri = property.asOWLObjectProperty().getIRI().toString();
                Map<String, Object> p = getOrCreateProperty(schema, propertiesByIri, iri, ontologyIri, "object");
                p.put("domain", axiom.getDomain().asOWLClass().getIRI().toString());
            }

            for (OWLObjectPropertyRangeAxiom axiom : ontology.getAxioms(AxiomType.OBJECT_PROPERTY_RANGE)) {
                OWLObjectPropertyExpression property = axiom.getProperty();
                if (property.isAnonymous() || axiom.getRange().isAnonymous()) {
                    continue;
                }
                String iri = property.asOWLObjectProperty().getIRI().toString();
                Map<String, Object> p = getOrCreateProperty(schema, propertiesByIri, iri, ontologyIri, "object");
                p.put("range", axiom.getRange().asOWLClass().getIRI().toString());
            }

            for (OWLDataPropertyDomainAxiom axiom : ontology.getAxioms(AxiomType.DATA_PROPERTY_DOMAIN)) {
                OWLDataPropertyExpression property = axiom.getProperty();
                if (property.isAnonymous() || axiom.getDomain().isAnonymous()) {
                    continue;
                }
                String iri = property.asOWLDataProperty().getIRI().toString();
                Map<String, Object> p = getOrCreateProperty(schema, propertiesByIri, iri, ontologyIri, "datatype");
                p.put("domain", axiom.getDomain().asOWLClass().getIRI().toString());
            }

            for (OWLDataPropertyRangeAxiom axiom : ontology.getAxioms(AxiomType.DATA_PROPERTY_RANGE)) {
                OWLDataPropertyExpression property = axiom.getProperty();
                if (property.isAnonymous()) {
                    continue;
                }
                String iri = property.asOWLDataProperty().getIRI().toString();
                Map<String, Object> p = getOrCreateProperty(schema, propertiesByIri, iri, ontologyIri, "datatype");
                p.put("range", dataRangeToIri(axiom.getRange()));
            }

            manager.removeOntology(ontology);
            log.info("Parsed OWL: {} classes, {} properties, {} subclasses, {} subproperties",
                    schema.classes.size(), schema.properties.size(),
                    schema.classHierarchy.size(), schema.subPropertyOf.size());

        } catch (Exception e) {
            log.error("Failed to parse OWL file: {}", owlPath, e);
        }

        return schema;
    }

    private File resolveFile(String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File("src/main/resources", path);
        }
        return file;
    }

    private String toLocalName(String iri, IRI baseIri) {
        String local = iri;
        if (baseIri != null && local.startsWith(baseIri.toString())) {
            local = local.substring(baseIri.toString().length());
        }
        local = local.replaceAll("^[/#]+", "");
        return local;
    }

    private Map<String, Object> getOrCreateProperty(OwlSchema schema,
                                                    Map<String, Map<String, Object>> propertiesByIri,
                                                    String iri,
                                                    IRI ontologyIri,
                                                    String type) {
        Map<String, Object> existing = propertiesByIri.get(iri);
        if (existing != null) {
            return existing;
        }

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("iri", iri);
        p.put("name", toLocalName(iri, ontologyIri));
        p.put("type", type);
        schema.properties.add(p);
        propertiesByIri.put(iri, p);
        return p;
    }

    private String dataRangeToIri(OWLDataRange range) {
        if (range instanceof OWLDatatype datatype) {
            return datatype.getIRI().toString();
        }
        return range.toString();
    }

    public static class OwlSchema {
        public final List<Map<String, Object>> classes = new ArrayList<>();
        public final List<Map<String, Object>> classHierarchy = new ArrayList<>();
        public final List<Map<String, Object>> properties = new ArrayList<>();
        public final List<Map<String, Object>> subPropertyOf = new ArrayList<>();
    }
}
