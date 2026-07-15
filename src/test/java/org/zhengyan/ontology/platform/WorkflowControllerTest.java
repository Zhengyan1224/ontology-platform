package org.zhengyan.ontology.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.zhengyan.ontology.platform.controller.WorkflowController;
import org.zhengyan.ontology.platform.model.Workflow;
import org.zhengyan.ontology.platform.service.WorkflowService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkflowController.class)
@AutoConfigureMockMvc(addFilters = false)
public class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WorkflowService workflowService;

    @Test
    void testListWorkflows() throws Exception {
        Workflow wf = new Workflow();
        wf.setId("wf-1");
        wf.setTenantId("test");
        wf.setName("Test Workflow");

        given(workflowService.listWorkflows("test")).willReturn(List.of(wf));

        mockMvc.perform(get("/api/v1/tenants/test/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("wf-1"));
    }

    @Test
    void testGetWorkflow() throws Exception {
        Workflow wf = new Workflow();
        wf.setId("wf-1");
        wf.setName("Test");

        given(workflowService.getWorkflow("wf-1")).willReturn(wf);

        mockMvc.perform(get("/api/v1/tenants/test/workflows/wf-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("wf-1"));
    }

    @Test
    void testGetWorkflowNotFound() throws Exception {
        given(workflowService.getWorkflow("nonexistent")).willReturn(null);

        mockMvc.perform(get("/api/v1/tenants/test/workflows/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateWorkflow() throws Exception {
        Workflow created = new Workflow();
        created.setId("new-wf");
        created.setTenantId("test");
        created.setName("New Workflow");

        given(workflowService.createWorkflow(any())).willReturn(created);

        mockMvc.perform(post("/api/v1/tenants/test/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "New Workflow",
                                "dagJson", "{\"nodes\":[{\"id\":\"s1\",\"actionId\":\"act-1\"}],\"edges\":[]}",
                                "enabled", true
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("new-wf"));
    }

    @Test
    void testCreateWorkflowInvalidDag() throws Exception {
        given(workflowService.createWorkflow(any()))
                .willThrow(new IllegalArgumentException("Cycle detected in DAG"));

        mockMvc.perform(post("/api/v1/tenants/test/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Bad",
                                "dagJson", "{\"nodes\":[{\"id\":\"s1\",\"actionId\":\"act-1\"},{\"id\":\"s2\",\"actionId\":\"act-2\"}],\"edges\":[{\"from\":\"s1\",\"to\":\"s2\"},{\"from\":\"s2\",\"to\":\"s1\"}]}"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_DAG"));
    }

    @Test
    void testDeleteWorkflow() throws Exception {
        given(workflowService.deleteWorkflow("wf-1")).willReturn(true);

        mockMvc.perform(delete("/api/v1/tenants/test/workflows/wf-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testExecuteWorkflow() throws Exception {
        Workflow wf = new Workflow();
        wf.setId("wf-1");
        wf.setName("Test");

        given(workflowService.getWorkflow("wf-1")).willReturn(wf);
        given(workflowService.execute(eq(wf)))
                .willReturn(new WorkflowService.WorkflowResult(true, "Workflow completed",
                        List.of(new WorkflowService.StepResult("s1", "act-1", true, "OK"))));

        mockMvc.perform(post("/api/v1/tenants/test/workflows/wf-1/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.steps[0].nodeId").value("s1"));
    }

    @Test
    void testValidateValidDag() throws Exception {
        given(workflowService.validateDag(anyString())).willReturn(null);

        mockMvc.perform(post("/api/v1/tenants/test/workflows/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dagJson", "{\"nodes\":[{\"id\":\"s1\",\"actionId\":\"act-1\"}],\"edges\":[]}"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void testValidateInvalidDag() throws Exception {
        given(workflowService.validateDag(anyString())).willReturn("Cycle detected in DAG");

        mockMvc.perform(post("/api/v1/tenants/test/workflows/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dagJson", "{\"nodes\":[{\"id\":\"s1\",\"actionId\":\"act-1\"},{\"id\":\"s2\",\"actionId\":\"act-2\"}],\"edges\":[{\"from\":\"s1\",\"to\":\"s2\"},{\"from\":\"s2\",\"to\":\"s1\"}]}"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }
}
