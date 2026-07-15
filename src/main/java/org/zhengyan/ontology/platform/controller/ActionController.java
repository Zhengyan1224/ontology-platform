package org.zhengyan.ontology.platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.zhengyan.ontology.platform.model.Action;
import org.zhengyan.ontology.platform.service.ActionService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ActionController {

    private static final String KEY_ERROR = "error";
    private static final String KEY_MESSAGE = "message";
    private static final String ACTION_NOT_FOUND = "ACTION_NOT_FOUND";

    private final ActionService actionService;

    public ActionController(ActionService actionService) {
        this.actionService = actionService;
    }

    @GetMapping("/tenants/{tenantId}/actions")
    public ResponseEntity<?> listActions(@PathVariable String tenantId) {
        List<Action> actions = actionService.listActions(tenantId);
        return ResponseEntity.ok(actions);
    }

    @GetMapping("/tenants/{tenantId}/actions/{actionId}")
    public ResponseEntity<?> getAction(@PathVariable String tenantId,
                                       @PathVariable String actionId) {
        Action action = actionService.getAction(actionId);
        if (action == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, ACTION_NOT_FOUND, KEY_MESSAGE, "Action not found: " + actionId));
        }
        return ResponseEntity.ok(action);
    }

    @PostMapping("/tenants/{tenantId}/actions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createAction(@PathVariable String tenantId,
                                          @RequestBody Action body) {
        body.setTenantId(tenantId);
        Action created = actionService.createAction(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/tenants/{tenantId}/actions/{actionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateAction(@PathVariable String tenantId,
                                          @PathVariable String actionId,
                                          @RequestBody Action body) {
        Action updated = actionService.updateAction(actionId, body);
        if (updated == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, ACTION_NOT_FOUND, KEY_MESSAGE, "Action not found: " + actionId));
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/tenants/{tenantId}/actions/{actionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteAction(@PathVariable String tenantId,
                                          @PathVariable String actionId) {
        boolean deleted = actionService.deleteAction(actionId);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, ACTION_NOT_FOUND, KEY_MESSAGE, "Action not found: " + actionId));
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tenants/{tenantId}/actions/{actionId}/execute")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> executeAction(@PathVariable String tenantId,
                                           @PathVariable String actionId,
                                           @RequestParam(defaultValue = "false") boolean dryRun) {
        Action action = actionService.getAction(actionId);
        if (action == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, ACTION_NOT_FOUND, KEY_MESSAGE, "Action not found: " + actionId));
        }
        try {
            ActionService.ActionResult result = actionService.execute(action, dryRun);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", result.success());
            resp.put(KEY_MESSAGE, result.message());
            resp.put("details", result.details());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put(KEY_ERROR, "ACTION_EXECUTION_FAILED");
            err.put(KEY_MESSAGE, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }
}
