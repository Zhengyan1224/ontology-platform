package org.zhengyan.ontology.platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.model.Rule;
import org.zhengyan.ontology.platform.model.RuleHistory;
import org.zhengyan.ontology.platform.service.RuleService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class RuleController {

    private static final String KEY_ERROR = "error";
    private static final String KEY_MESSAGE = "message";
    private static final String RULE_NOT_FOUND = "RULE_NOT_FOUND";

    private final RuleService ruleService;
    private final TenantConfig tenantConfig;

    public RuleController(RuleService ruleService, TenantConfig tenantConfig) {
        this.ruleService = ruleService;
        this.tenantConfig = tenantConfig;
    }

    @GetMapping("/tenants/{tenantId}/rules")
    public ResponseEntity<?> listRules(@PathVariable String tenantId) {
        List<Rule> rules = ruleService.listRules(tenantId);
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/tenants/{tenantId}/rules/{ruleId}")
    public ResponseEntity<?> getRule(@PathVariable String tenantId,
                                     @PathVariable String ruleId) {
        Rule rule = ruleService.getRule(ruleId);
        if (rule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, RULE_NOT_FOUND, KEY_MESSAGE, "Rule not found: " + ruleId));
        }
        return ResponseEntity.ok(rule);
    }

    @PostMapping("/tenants/{tenantId}/rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createRule(@PathVariable String tenantId,
                                        @RequestBody Rule body) {
        body.setTenantId(tenantId);
        Rule created = ruleService.createRule(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/tenants/{tenantId}/rules/{ruleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateRule(@PathVariable String tenantId,
                                        @PathVariable String ruleId,
                                        @RequestBody Rule body) {
        Rule updated = ruleService.updateRule(ruleId, body);
        if (updated == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, RULE_NOT_FOUND, KEY_MESSAGE, "Rule not found: " + ruleId));
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/tenants/{tenantId}/rules/{ruleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteRule(@PathVariable String tenantId,
                                        @PathVariable String ruleId) {
        boolean deleted = ruleService.deleteRule(ruleId);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, RULE_NOT_FOUND, KEY_MESSAGE, "Rule not found: " + ruleId));
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tenants/{tenantId}/rules/{ruleId}/evaluate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> evaluateRule(@PathVariable String tenantId,
                                          @PathVariable String ruleId,
                                          @RequestBody Map<String, Object> context) {
        Rule rule = ruleService.getRule(ruleId);
        if (rule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, RULE_NOT_FOUND, KEY_MESSAGE, "Rule not found: " + ruleId));
        }
        RuleService.EvaluateResult result = ruleService.evaluate(rule, context);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("passed", result.passed());
        resp.put("trace", result.trace());
        return ResponseEntity.ok(resp);
    }

    @PatchMapping("/tenants/{tenantId}/rules/{ruleId}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleRule(@PathVariable String tenantId,
                                        @PathVariable String ruleId) {
        Rule rule = ruleService.getRule(ruleId);
        if (rule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, RULE_NOT_FOUND, KEY_MESSAGE, "Rule not found: " + ruleId));
        }
        rule.setEnabled(!rule.isEnabled());
        ruleService.updateRule(ruleId, rule);
        return ResponseEntity.ok(Map.of("enabled", rule.isEnabled()));
    }

    @GetMapping("/tenants/{tenantId}/rules/{ruleId}/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getHistory(@PathVariable String tenantId,
                                        @PathVariable String ruleId) {
        List<RuleHistory> history = ruleService.getHistory(ruleId);
        return ResponseEntity.ok(history);
    }
}
