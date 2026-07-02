package org.zhengyan.ontology.platform.service;

import java.util.List;
import java.util.Map;

public interface SchemaProvider {
    List<Map<String, Object>> getClasses();
    List<Map<String, Object>> getClassHierarchy();
    List<Map<String, Object>> getProperties();
    List<Map<String, Object>> getMappings();
    Map<String, Object> getAll();
}
