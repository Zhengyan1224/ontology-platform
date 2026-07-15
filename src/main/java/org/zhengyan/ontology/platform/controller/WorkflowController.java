package org.zhengyan.ontology.platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.zhengyan.ontology.platform.model.Workflow;
import org.zhengyan.ontology.platform.service.WorkflowService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class WorkflowController {

    private static final String KEY_ERROR = "error";
    private static final String KEY_MESSAGE = "message";
    private static final String WORKFLOW_NOT_FOUND = "WORKFLOW_NOT_FOUND";

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/tenants/{tenantId}/workflows")
    public ResponseEntity<?> listWorkflows(@PathVariable String tenantId) {
        List<Workflow> workflows = workflowService.listWorkflows(tenantId);
        return ResponseEntity.ok(workflows);
    }

    @GetMapping("/tenants/{tenantId}/workflows/{workflowId}")
    public ResponseEntity<?> getWorkflow(@PathVariable String tenantId,
                                         @PathVariable String workflowId) {
        Workflow wf = workflowService.getWorkflow(workflowId);
        if (wf == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, WORKFLOW_NOT_FOUND, KEY_MESSAGE, "Workflow not found: " + workflowId));
        }
        return ResponseEntity.ok(wf);
    }

    @PostMapping("/tenants/{tenantId}/workflows")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createWorkflow(@PathVariable String tenantId,
                                            @RequestBody Workflow body) {
        body.setTenantId(tenantId);
        try {
            Workflow created = workflowService.createWorkflow(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of(KEY_ERROR, "INVALID_DAG", KEY_MESSAGE, e.getMessage()));
        }
    }

    @PutMapping("/tenants/{tenantId}/workflows/{workflowId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateWorkflow(@PathVariable String tenantId,
                                            @PathVariable String workflowId,
                                            @RequestBody Workflow body) {
        try {
            Workflow updated = workflowService.updateWorkflow(workflowId, body);
            if (updated == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(KEY_ERROR, WORKFLOW_NOT_FOUND, KEY_MESSAGE, "Workflow not found: " + workflowId));
            }
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of(KEY_ERROR, "INVALID_DAG", KEY_MESSAGE, e.getMessage()));
        }
    }

    @DeleteMapping("/tenants/{tenantId}/workflows/{workflowId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteWorkflow(@PathVariable String tenantId,
                                            @PathVariable String workflowId) {
        boolean deleted = workflowService.deleteWorkflow(workflowId);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, WORKFLOW_NOT_FOUND, KEY_MESSAGE, "Workflow not found: " + workflowId));
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tenants/{tenantId}/workflows/{workflowId}/execute")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> executeWorkflow(@PathVariable String tenantId,
                                             @PathVariable String workflowId) {
        Workflow wf = workflowService.getWorkflow(workflowId);
        if (wf == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, WORKFLOW_NOT_FOUND, KEY_MESSAGE, "Workflow not found: " + workflowId));
        }
        try {
            WorkflowService.WorkflowResult result = workflowService.execute(wf);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", result.success());
            resp.put(KEY_MESSAGE, result.message());
            resp.put("steps", result.steps());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put(KEY_ERROR, "WORKFLOW_EXECUTION_FAILED");
            err.put(KEY_MESSAGE, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    @PostMapping("/tenants/{tenantId}/workflows/validate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> validateDag(@RequestBody Map<String, Object> body) {
        String dagJson = (String) body.get("dagJson");
        String error = workflowService.validateDag(dagJson);
        Map<String, Object> resp = new LinkedHashMap<>();
        if (error == null) {
            resp.put("valid", true);
        } else {
            resp.put("valid", false);
            resp.put(KEY_MESSAGE, error);
        }
        return ResponseEntity.ok(resp);
    }
}
