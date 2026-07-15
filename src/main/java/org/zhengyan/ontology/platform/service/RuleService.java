package org.zhengyan.ontology.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.model.Rule;
import org.zhengyan.ontology.platform.model.RuleHistory;
import org.zhengyan.ontology.platform.repository.RuleHistoryRepository;
import org.zhengyan.ontology.platform.repository.RuleRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RuleService {

    private static final Logger log = LoggerFactory.getLogger(RuleService.class);

    private final RuleRepository ruleRepository;
    private final RuleHistoryRepository ruleHistoryRepository;
    private final ObjectMapper objectMapper;
    private final ExpressionParser parser;

    public RuleService(RuleRepository ruleRepository,
                       RuleHistoryRepository ruleHistoryRepository,
                       ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.ruleHistoryRepository = ruleHistoryRepository;
        this.objectMapper = objectMapper;
        SpelParserConfiguration config = new SpelParserConfiguration(true, true);
        this.parser = new SpelExpressionParser(config);
    }

    public List<Rule> listRules(String tenantId) {
        return ruleRepository.findByTenantId(tenantId);
    }

    public Rule getRule(String id) {
        return ruleRepository.findById(id);
    }

    public Rule createRule(Rule rule) {
        ruleRepository.save(rule);
        return rule;
    }

    public Rule updateRule(String id, Rule update) {
        Rule existing = ruleRepository.findById(id);
        if (existing == null) return null;
        existing.setName(update.getName());
        existing.setDescription(update.getDescription());
        existing.setConditionExpr(update.getConditionExpr());
        existing.setActionRefs(update.getActionRefs());
        existing.setEnabled(update.isEnabled());
        ruleRepository.save(existing);
        return existing;
    }

    public boolean deleteRule(String id) {
        Rule existing = ruleRepository.findById(id);
        if (existing == null) return false;
        ruleHistoryRepository.deleteByRuleId(id);
        ruleRepository.deleteById(id);
        return true;
    }

    public EvaluateResult evaluate(Rule rule, Map<String, Object> context) {
        List<String> trace = new ArrayList<>();
        boolean passed;
        try {
            StandardEvaluationContext evalContext = new StandardEvaluationContext();
            context.forEach(evalContext::setVariable);

            passed = parser.parseExpression(rule.getConditionExpr())
                    .getValue(evalContext, Boolean.class);
            trace.add(rule.getConditionExpr() + " → " + passed);
        } catch (Exception e) {
            passed = false;
            trace.add(rule.getConditionExpr() + " → ERROR: " + e.getMessage());
        }

        RuleHistory history = new RuleHistory();
        history.setRuleId(rule.getId());
        history.setTenantId(rule.getTenantId());
        history.setContextJson(toJson(context));
        history.setPassed(passed);
        history.setTraceJson(toJson(trace));
        ruleHistoryRepository.save(history);

        log.info("Rule [{}] evaluated: {} (passed={})", rule.getId(), rule.getName(), passed);
        return new EvaluateResult(passed, trace);
    }

    public List<RuleHistory> getHistory(String ruleId) {
        return ruleHistoryRepository.findByRuleId(ruleId);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public record EvaluateResult(boolean passed, List<String> trace) {}
}
