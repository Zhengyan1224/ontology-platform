package org.zhengyan.ontology.platform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zhengyan.ontology.platform.service.MappingAssistantService;

@RestController
@RequestMapping("/api/v1")
public class MappingAssistantController {

    private final MappingAssistantService mappingAssistantService;

    public MappingAssistantController(MappingAssistantService mappingAssistantService) {
        this.mappingAssistantService = mappingAssistantService;
    }

    @PostMapping("/tenants/{tenantId}/mapping-assistant/draft")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MappingAssistantService.DraftResponse> createDraft(
            @PathVariable String tenantId,
            @RequestBody(required = false) MappingAssistantService.DraftRequest request) {
        return ResponseEntity.ok(mappingAssistantService.createDraft(tenantId, request));
    }

    @PutMapping("/tenants/{tenantId}/mapping-assistant/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MappingAssistantService.DraftResponse> updateConfig(
            @PathVariable String tenantId,
            @RequestBody MappingAssistantService.EditableConfig config) {
        return ResponseEntity.ok(mappingAssistantService.applyConfig(tenantId, config));
    }
}
