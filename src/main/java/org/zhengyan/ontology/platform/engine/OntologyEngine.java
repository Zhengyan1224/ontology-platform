package org.zhengyan.ontology.platform.engine;

import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.zhengyan.ontology.platform.model.Tenant;

import java.util.Map;

public interface OntologyEngine {

    void initialize(Tenant tenant) throws Exception;

    SparqlQueryResult executeQuery(String sparql) throws Exception;

    String translateToSql(String sparql) throws Exception;

    Map<String, Object> getOntologyInfo();

    boolean isHealthy();

    String checkHealth();

    void destroy();

    String getTenantId();
}
