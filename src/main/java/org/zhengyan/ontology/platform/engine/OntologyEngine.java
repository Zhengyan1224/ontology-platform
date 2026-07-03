package org.zhengyan.ontology.platform.engine;

import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.zhengyan.ontology.platform.model.Tenant;

import java.util.Map;

/**
 * @author 郑炎 Zheng Yan
 */
public interface OntologyEngine {

    /**
     * {@inheritDoc}
     */
    void initialize(Tenant tenant) throws Exception;

    /**
     * {@inheritDoc}
     */
    SparqlQueryResult executeQuery(String sparql) throws Exception;

    /**
     * {@inheritDoc}
     */
    String translateToSql(String sparql) throws Exception;

    /**
     * {@inheritDoc}
     */
    Map<String, Object> getOntologyInfo();

    /**
     * {@inheritDoc}
     */
    boolean isHealthy();

    /**
     * {@inheritDoc}
     */
    String checkHealth();

    /**
     * {@inheritDoc}
     */
    void destroy();

    /**
     * {@inheritDoc}
     */
    String getTenantId();
}
