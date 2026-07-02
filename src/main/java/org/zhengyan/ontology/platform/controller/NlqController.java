package org.zhengyan.ontology.platform.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.zhengyan.ontology.platform.model.NlqRequest;
import org.zhengyan.ontology.platform.service.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class NlqController {

    private static final Logger log = LoggerFactory.getLogger(NlqController.class);

    private final NaturalLanguageQueryService nlqService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final long streamTimeout;

    public NlqController(NaturalLanguageQueryService nlqService,
                         AuditService auditService,
                         MetricsService metricsService,
                         @Value("${ontology.nlq.stream.timeout:60000}") long streamTimeout) {
        this.nlqService = nlqService;
        this.auditService = auditService;
        this.metricsService = metricsService;
        this.streamTimeout = streamTimeout;
    }

    @PostMapping(value = "/tenants/{tenantId}/nlq",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NlqResult> query(
            @PathVariable String tenantId,
            @RequestBody NlqRequest request) throws Exception {
        long start = System.currentTimeMillis();
        NlqResult result = nlqService.answer(tenantId, request.getQuestion(), request.getSessionId());
        long elapsed = System.currentTimeMillis() - start;

        auditService.recordNlqQuery(tenantId, request.getQuestion(),
                result.getSparql(), elapsed, true, null,
                result.getResults().size());
        metricsService.recordNlq(tenantId, elapsed, true, result.getMode());

        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/tenants/{tenantId}/nlq/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamQuery(
            @PathVariable String tenantId,
            @RequestParam String question,
            @RequestParam(required = false) String sessionId) {
        SseEmitter emitter = new SseEmitter(streamTimeout);

        emitter.onCompletion(() -> log.info("SSE stream completed for tenant '{}'", tenantId));
        emitter.onTimeout(() -> {
            log.warn("SSE stream timed out for tenant '{}'", tenantId);
            emitter.complete();
        });
        emitter.onError(ex -> log.error("SSE stream error for tenant '{}': {}", tenantId, ex.getMessage()));

        nlqService.streamAnswer(tenantId, question, sessionId, emitter);

        return emitter;
    }
}
