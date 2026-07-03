package org.zhengyan.ontology.platform.service;

import java.util.List;
import java.util.Map;

/**
 * @author 郑炎 Zheng Yan
 */
public interface SchemaProvider {
    /**
     * {@inheritDoc}
     */
    List<Map<String, Object>> getClasses();
    /**
     * {@inheritDoc}
     */
    List<Map<String, Object>> getClassHierarchy();
    /**
     * {@inheritDoc}
     */
    List<Map<String, Object>> getProperties();
    /**
     * {@inheritDoc}
     */
    List<Map<String, Object>> getMappings();
    /**
     * {@inheritDoc}
     */
    Map<String, Object> getAll();
}
