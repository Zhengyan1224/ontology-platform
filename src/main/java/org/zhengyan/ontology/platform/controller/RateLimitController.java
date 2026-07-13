package org.zhengyan.ontology.platform.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zhengyan.ontology.platform.service.RateLimitFilter;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnBean(RateLimitFilter.class)
public class RateLimitController {

    private final RateLimitFilter rateLimitFilter;

    public RateLimitController(RateLimitFilter rateLimitFilter) {
        this.rateLimitFilter = rateLimitFilter;
    }

    @GetMapping("/rate-limit/status")
    public ResponseEntity<Map<String, Object>> status(HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capacity", rateLimitFilter.getCapacity());
        result.put("refillTokens", rateLimitFilter.getRefillTokens());
        result.put("refillPeriodSeconds", rateLimitFilter.getRefillPeriodSeconds());
        result.put("availableTokens", rateLimitFilter.getAvailableTokens(clientIp));
        result.put("activeClients", rateLimitFilter.getActiveClients());
        return ResponseEntity.ok(result);
    }
}
