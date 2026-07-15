package org.zhengyan.ontology.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.zhengyan.ontology.platform.controller.ActionController;
import org.zhengyan.ontology.platform.model.Action;
import org.zhengyan.ontology.platform.service.ActionService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ActionController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ActionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ActionService actionService;

    @Test
    void testListActions() throws Exception {
        Action action = new Action();
        action.setId("act-1");
        action.setTenantId("test");
        action.setName("Test Action");
        action.setType("sql_exec");

        given(actionService.listActions("test")).willReturn(List.of(action));

        mockMvc.perform(get("/api/v1/tenants/test/actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("act-1"))
                .andExpect(jsonPath("$[0].name").value("Test Action"));
    }

    @Test
    void testGetAction() throws Exception {
        Action action = new Action();
        action.setId("act-1");
        action.setName("Test");

        given(actionService.getAction("act-1")).willReturn(action);

        mockMvc.perform(get("/api/v1/tenants/test/actions/act-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("act-1"));
    }

    @Test
    void testGetActionNotFound() throws Exception {
        given(actionService.getAction("nonexistent")).willReturn(null);

        mockMvc.perform(get("/api/v1/tenants/test/actions/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateAction() throws Exception {
        Action created = new Action();
        created.setId("new-act");
        created.setTenantId("test");
        created.setName("New Action");
        created.setType("sql_exec");

        given(actionService.createAction(any())).willReturn(created);

        mockMvc.perform(post("/api/v1/tenants/test/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "New Action",
                                "type", "sql_exec",
                                "configJson", "{\"sql\":\"SELECT 1\"}"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("new-act"));
    }

    @Test
    void testUpdateAction() throws Exception {
        Action updated = new Action();
        updated.setId("act-1");
        updated.setName("Updated");
        updated.setType("api_call");

        given(actionService.updateAction(eq("act-1"), any())).willReturn(updated);

        mockMvc.perform(put("/api/v1/tenants/test/actions/act-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Updated",
                                "type", "api_call"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void testDeleteAction() throws Exception {
        given(actionService.deleteAction("act-1")).willReturn(true);

        mockMvc.perform(delete("/api/v1/tenants/test/actions/act-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteActionNotFound() throws Exception {
        given(actionService.deleteAction("nonexistent")).willReturn(false);

        mockMvc.perform(delete("/api/v1/tenants/test/actions/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testExecuteAction() throws Exception {
        Action action = new Action();
        action.setId("act-1");
        action.setName("SQL Exec");
        action.setType("sql_exec");

        given(actionService.getAction("act-1")).willReturn(action);
        given(actionService.execute(eq(action), eq(false)))
                .willReturn(new ActionService.ActionResult(true, "SQL executed",
                        Map.of("success", true, "rowCount", 5)));

        mockMvc.perform(post("/api/v1/tenants/test/actions/act-1/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("SQL executed"));
    }

    @Test
    void testExecuteActionDryRun() throws Exception {
        Action action = new Action();
        action.setId("act-1");

        given(actionService.getAction("act-1")).willReturn(action);
        given(actionService.execute(eq(action), eq(true)))
                .willReturn(new ActionService.ActionResult(true, "Dry-run preview", null));

        mockMvc.perform(post("/api/v1/tenants/test/actions/act-1/execute?dryRun=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Dry-run preview"));
    }

    @Test
    void testExecuteActionNotFound() throws Exception {
        given(actionService.getAction("nonexistent")).willReturn(null);

        mockMvc.perform(post("/api/v1/tenants/test/actions/nonexistent/execute"))
                .andExpect(status().isNotFound());
    }
}
