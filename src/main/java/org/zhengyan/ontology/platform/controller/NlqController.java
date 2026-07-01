package org.zhengyan.ontology.platform.controller;

import org.zhengyan.ontology.platform.model.NlqRequest;
import org.zhengyan.ontology.platform.service.AuditService;
import org.zhengyan.ontology.platform.service.MetricsService;
import org.zhengyan.ontology.platform.service.NaturalLanguageQueryService;
import org.zhengyan.ontology.platform.service.NlqResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class NlqController {

    private static final Logger log = LoggerFactory.getLogger(NlqController.class);

    private final NaturalLanguageQueryService nlqService;
    private final AuditService auditService;
    private final MetricsService metricsService;

    public NlqController(NaturalLanguageQueryService nlqService,
                         AuditService auditService,
                         MetricsService metricsService) {
        this.nlqService = nlqService;
        this.auditService = auditService;
        this.metricsService = metricsService;
    }

    @PostMapping(value = "/tenants/{tenantId}/nlq",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NlqResult> query(
            @PathVariable String tenantId,
            @RequestBody NlqRequest request) throws Exception {
        long start = System.currentTimeMillis();
        NlqResult result = nlqService.answer(tenantId, request.getQuestion());
        long elapsed = System.currentTimeMillis() - start;

        auditService.recordNlqQuery(tenantId, request.getQuestion(),
                result.getSparql(), elapsed, true, null,
                result.getResults().size());
        metricsService.recordNlq(tenantId, elapsed, true, result.getMode());

        return ResponseEntity.ok(result);
    }
}
