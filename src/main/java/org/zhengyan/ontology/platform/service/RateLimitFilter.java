package org.zhengyan.ontology.platform.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final long capacity;
    private final long refillTokens;
    private final long refillPeriodSeconds;
    private final AuditService auditService;

    public RateLimitFilter(long capacity, long refillTokens, long refillPeriodSeconds, AuditService auditService) {
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodSeconds = refillPeriodSeconds;
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!isRateLimited(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(clientIp, this::newBucket);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            auditService.recordAuthEvent(clientIp, "RATE_LIMITED", false, path);
            response.setStatus(429);
            response.getWriter().write("Too many requests - rate limit exceeded");
        }
    }

    private boolean isRateLimited(String path) {
        return path.matches(".*/auth/login|.*/auth/reinitialize|.*/audit-clear.*");
    }

    private Bucket newBucket(String clientIp) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.greedy(refillTokens, Duration.ofSeconds(refillPeriodSeconds))))
                .build();
    }
}
