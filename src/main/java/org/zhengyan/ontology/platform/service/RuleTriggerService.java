package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.model.Rule;
import org.zhengyan.ontology.platform.repository.RuleRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RuleTriggerService {

    private static final Logger log = LoggerFactory.getLogger(RuleTriggerService.class);

    private final RuleRepository ruleRepository;
    private final RuleService ruleService;

    public RuleTriggerService(RuleRepository ruleRepository, RuleService ruleService) {
        this.ruleRepository = ruleRepository;
        this.ruleService = ruleService;
    }

    public void onSparqlQuery(String tenantId, String sparql, long executionTimeMs, int resultCount) {
        List<Rule> enabledRules;
        try {
            enabledRules = ruleRepository.findByTenantId(tenantId).stream()
                    .filter(Rule::isEnabled)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load rules for trigger [{}]: {}", tenantId, e.getMessage());
            return;
        }

        if (enabledRules.isEmpty()) return;

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("queryType", detectQueryType(sparql));
        context.put("executionTimeMs", executionTimeMs);
        context.put("resultCount", resultCount);
        context.put("sparql", sparql);

        for (Rule rule : enabledRules) {
            try {
                RuleService.EvaluateResult result = ruleService.evaluate(rule, context);
                if (result.passed()) {
                    log.info("Rule [{}] triggered by SPARQL query on tenant [{}]", rule.getName(), tenantId);
                }
            } catch (Exception e) {
                log.warn("Rule [{}] trigger evaluation failed: {}", rule.getName(), e.getMessage());
            }
        }
    }

    private String detectQueryType(String sparql) {
        String trimmed = sparql.strip().toUpperCase();
        if (trimmed.startsWith("SELECT")) return "SELECT";
        if (trimmed.startsWith("CONSTRUCT")) return "CONSTRUCT";
        if (trimmed.startsWith("ASK")) return "ASK";
        if (trimmed.startsWith("DESCRIBE")) return "DESCRIBE";
        return "UNKNOWN";
    }
}
