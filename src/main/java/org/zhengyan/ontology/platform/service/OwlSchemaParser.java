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

            for (OWLClass cls : ontology.getClassesInSignature()) {
                String iri = cls.getIRI().toString();
                schema.classes.add(Map.of("iri", iri, "name", toLocalName(iri, ontologyIri)));
            }

            for (OWLSubClassOfAxiom axiom : ontology.getAxioms(AxiomType.SUBCLASS_OF)) {
                String child = axiom.getSubClass().asOWLClass().getIRI().toString();
                String parent = axiom.getSuperClass().asOWLClass().getIRI().toString();
                schema.classHierarchy.add(Map.of("child", child, "parent", parent));
            }

            for (OWLSubPropertyAxiom<?> axiom : ontology.getAxioms(AxiomType.SUB_OBJECT_PROPERTY)) {
                OWLObjectPropertyExpression sub = (OWLObjectPropertyExpression) axiom.getSubProperty();
                OWLObjectPropertyExpression sup = (OWLObjectPropertyExpression) axiom.getSuperProperty();
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
            }

            for (OWLDataProperty prop : ontology.getDataPropertiesInSignature()) {
                String iri = prop.getIRI().toString();
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("iri", iri);
                p.put("name", toLocalName(iri, ontologyIri));
                p.put("type", "datatype");
                schema.properties.add(p);
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

    public static class OwlSchema {
        public final List<Map<String, Object>> classes = new ArrayList<>();
        public final List<Map<String, Object>> classHierarchy = new ArrayList<>();
        public final List<Map<String, Object>> properties = new ArrayList<>();
        public final List<Map<String, Object>> subPropertyOf = new ArrayList<>();
    }
}
