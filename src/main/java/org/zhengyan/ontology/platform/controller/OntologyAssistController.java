package org.zhengyan.ontology.platform.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zhengyan.ontology.platform.model.OntologyProposal;
import org.zhengyan.ontology.platform.service.LlmOntologyAssistService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/ontology-assist")
public class OntologyAssistController {

    private static final Logger log = LoggerFactory.getLogger(OntologyAssistController.class);
    private final LlmOntologyAssistService assistService;

    public OntologyAssistController(LlmOntologyAssistService assistService) {
        this.assistService = assistService;
    }

    @PostMapping("/extract")
    public ResponseEntity<OntologyProposal> extract(@PathVariable String tenantId,
                                                    @RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "Ontology Proposal");
        String description = body.get("description");
        if (description == null || description.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        OntologyProposal proposal = assistService.extractFromDescription(tenantId, title, description);
        return ResponseEntity.status(HttpStatus.CREATED).body(proposal);
    }

    @GetMapping("/ddl-hints")
    public ResponseEntity<LlmOntologyAssistService.DdlHintsResult> getDdlHints(
            @PathVariable String tenantId) {
        var hints = assistService.getDdlHints(tenantId);
        if (!hints.success()) {
            return ResponseEntity.badRequest().body(hints);
        }
        return ResponseEntity.ok(hints);
    }

    @PostMapping("/generate-from-ddl")
    public ResponseEntity<OntologyProposal> generateFromDdl(@PathVariable String tenantId,
                                                            @RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.getOrDefault("title", "DDL-generated Ontology") : "DDL-generated Ontology";
        OntologyProposal proposal = assistService.generateFromDdl(tenantId, title);
        return ResponseEntity.status(HttpStatus.CREATED).body(proposal);
    }

    @GetMapping("/proposals")
    public ResponseEntity<List<OntologyProposal>> listProposals(@PathVariable String tenantId) {
        return ResponseEntity.ok(assistService.listProposals(tenantId));
    }

    @GetMapping("/proposals/{proposalId}")
    public ResponseEntity<OntologyProposal> getProposal(@PathVariable String tenantId,
                                                        @PathVariable String proposalId) {
        OntologyProposal p = assistService.getProposal(tenantId, proposalId);
        if (p == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(p);
    }

    @PostMapping("/proposals/{proposalId}/apply")
    public ResponseEntity<OntologyProposal> applyProposal(@PathVariable String tenantId,
                                                          @PathVariable String proposalId) {
        try {
            OntologyProposal p = assistService.applyProposal(tenantId, proposalId);
            if (p == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(p);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/proposals/{proposalId}/reject")
    public ResponseEntity<OntologyProposal> rejectProposal(@PathVariable String tenantId,
                                                           @PathVariable String proposalId,
                                                           @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        OntologyProposal p = assistService.rejectProposal(tenantId, proposalId, reason);
        if (p == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(p);
    }

    @DeleteMapping("/proposals/{proposalId}")
    public ResponseEntity<Void> deleteProposal(@PathVariable String tenantId,
                                               @PathVariable String proposalId) {
        boolean deleted = assistService.deleteProposal(tenantId, proposalId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
