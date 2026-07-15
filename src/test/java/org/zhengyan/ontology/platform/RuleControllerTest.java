package org.zhengyan.ontology.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.controller.RuleController;
import org.zhengyan.ontology.platform.model.Rule;
import org.zhengyan.ontology.platform.model.RuleHistory;
import org.zhengyan.ontology.platform.service.RuleService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RuleController.class)
@AutoConfigureMockMvc(addFilters = false)
public class RuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RuleService ruleService;

    @MockitoBean
    private TenantConfig tenantConfig;

    @Test
    void testListRules() throws Exception {
        Rule rule = new Rule();
        rule.setId("rule-1");
        rule.setTenantId("test");
        rule.setName("Test Rule");
        rule.setConditionExpr("price > 100");
        rule.setEnabled(true);

        given(ruleService.listRules("test")).willReturn(List.of(rule));

        mockMvc.perform(get("/api/v1/tenants/test/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("rule-1"))
                .andExpect(jsonPath("$[0].name").value("Test Rule"));
    }

    @Test
    void testGetRule() throws Exception {
        Rule rule = new Rule();
        rule.setId("rule-1");
        rule.setTenantId("test");
        rule.setName("Test Rule");

        given(ruleService.getRule("rule-1")).willReturn(rule);

        mockMvc.perform(get("/api/v1/tenants/test/rules/rule-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("rule-1"));
    }

    @Test
    void testGetRuleNotFound() throws Exception {
        given(ruleService.getRule("nonexistent")).willReturn(null);

        mockMvc.perform(get("/api/v1/tenants/test/rules/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateRule() throws Exception {
        Rule created = new Rule();
        created.setId("new-rule");
        created.setTenantId("test");
        created.setName("New Rule");
        created.setConditionExpr("x > 10");
        created.setEnabled(true);

        given(ruleService.createRule(any())).willReturn(created);

        mockMvc.perform(post("/api/v1/tenants/test/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "New Rule",
                                "conditionExpr", "x > 10",
                                "enabled", true
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("new-rule"));
    }

    @Test
    void testUpdateRule() throws Exception {
        Rule updated = new Rule();
        updated.setId("rule-1");
        updated.setName("Updated");
        updated.setConditionExpr("y < 50");
        updated.setEnabled(false);

        given(ruleService.updateRule(eq("rule-1"), any())).willReturn(updated);

        mockMvc.perform(put("/api/v1/tenants/test/rules/rule-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Updated",
                                "conditionExpr", "y < 50",
                                "enabled", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void testUpdateRuleNotFound() throws Exception {
        given(ruleService.updateRule(eq("nonexistent"), any())).willReturn(null);

        mockMvc.perform(put("/api/v1/tenants/test/rules/nonexistent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Updated",
                                "conditionExpr", "y < 50",
                                "enabled", false
                        ))))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteRule() throws Exception {
        given(ruleService.deleteRule("rule-1")).willReturn(true);

        mockMvc.perform(delete("/api/v1/tenants/test/rules/rule-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteRuleNotFound() throws Exception {
        given(ruleService.deleteRule("nonexistent")).willReturn(false);

        mockMvc.perform(delete("/api/v1/tenants/test/rules/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testEvaluateRule() throws Exception {
        Rule rule = new Rule();
        rule.setId("rule-1");
        rule.setName("Price Check");
        rule.setConditionExpr("price > 100");

        given(ruleService.getRule("rule-1")).willReturn(rule);
        given(ruleService.evaluate(eq(rule), any()))
                .willReturn(new RuleService.EvaluateResult(true, List.of("price > 100 → true")));

        mockMvc.perform(post("/api/v1/tenants/test/rules/rule-1/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("price", 150))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.trace[0]").value("price > 100 → true"));
    }

    @Test
    void testEvaluateRuleNotFound() throws Exception {
        given(ruleService.getRule("nonexistent")).willReturn(null);

        mockMvc.perform(post("/api/v1/tenants/test/rules/nonexistent/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("price", 150))))
                .andExpect(status().isNotFound());
    }

    @Test
    void testToggleRule() throws Exception {
        Rule rule = new Rule();
        rule.setId("rule-1");
        rule.setEnabled(false);

        given(ruleService.getRule("rule-1")).willReturn(rule);
        given(ruleService.updateRule(eq("rule-1"), any())).willReturn(rule);

        mockMvc.perform(patch("/api/v1/tenants/test/rules/rule-1/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void testGetHistory() throws Exception {
        RuleHistory history = new RuleHistory();
        history.setId("hist-1");
        history.setRuleId("rule-1");
        history.setPassed(true);

        given(ruleService.getHistory("rule-1")).willReturn(List.of(history));

        mockMvc.perform(get("/api/v1/tenants/test/rules/rule-1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("hist-1"))
                .andExpect(jsonPath("$[0].passed").value(true));
    }
}
