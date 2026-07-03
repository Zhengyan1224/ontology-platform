package org.zhengyan.ontology.platform;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zhengyan.ontology.platform.service.AuditService;
import org.zhengyan.ontology.platform.service.RateLimitFilter;

import java.io.PrintWriter;

import static org.mockito.Mockito.*;

public class RateLimitFilterTest {

    private AuditService auditService;
    private RateLimitFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;
    private PrintWriter printWriter;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        filter = new RateLimitFilter(10, 10, 60, auditService);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        printWriter = mock(PrintWriter.class);
    }

    @Test
    void nonRateLimitedPathPassesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/health");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void rateLimitedPathConsumesToken() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rateLimitExceededReturns429() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("192.168.1.2");
        when(response.getWriter()).thenReturn(printWriter);

        RateLimitFilter smallFilter = new RateLimitFilter(1, 1, 60, auditService);
        smallFilter.doFilter(request, response, filterChain);
        smallFilter.doFilter(request, response, filterChain);

        verify(response).setStatus(429);
        verify(printWriter).write("Too many requests - rate limit exceeded");
        verify(filterChain, times(1)).doFilter(request, response);
        verify(auditService).recordAuthEvent("192.168.1.2", "RATE_LIMITED", false, "/api/v1/auth/login");
    }

    @Test
    void differentIpsHaveSeparateBuckets() throws Exception {
        HttpServletRequest req1 = mock(HttpServletRequest.class);
        when(req1.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(req1.getRemoteAddr()).thenReturn("10.0.0.1");

        HttpServletRequest req2 = mock(HttpServletRequest.class);
        when(req2.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(req2.getRemoteAddr()).thenReturn("10.0.0.2");

        filter.doFilter(req1, response, filterChain);
        filter.doFilter(req2, response, filterChain);

        verify(filterChain, times(2)).doFilter(any(), any());
    }
}
