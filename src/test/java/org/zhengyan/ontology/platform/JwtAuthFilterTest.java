package org.zhengyan.ontology.platform;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.zhengyan.ontology.platform.repository.JwtBlacklistRepository;
import org.zhengyan.ontology.platform.service.AuditService;
import org.zhengyan.ontology.platform.service.JwtAuthFilter;
import org.zhengyan.ontology.platform.service.JwtService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class JwtAuthFilterTest {

    private JwtService jwtService;
    private JwtBlacklistRepository jwtBlacklistRepository;
    private AuditService auditService;
    private JwtAuthFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        jwtBlacklistRepository = mock(JwtBlacklistRepository.class);
        auditService = mock(AuditService.class);
        filter = new JwtAuthFilter(jwtService, jwtBlacklistRepository, auditService);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthorizationHeaderPassesThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void invalidTokenPassesThroughWithoutAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(jwtService.isTokenValid("invalid-token")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void validTokenSetsAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractTokenId("valid-token")).thenReturn("jti-1");
        when(jwtService.extractUsername("valid-token")).thenReturn("admin");
        when(jwtService.extractAuthorities("valid-token")).thenReturn(List.of("ROLE_ADMIN"));
        when(jwtService.extractTenants("valid-token")).thenReturn("*");
        when(jwtBlacklistRepository.exists("jti-1")).thenReturn(false);
        when(jwtBlacklistRepository.isSubjectRevoked("admin")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals("*", SecurityContextHolder.getContext().getAuthentication().getDetails());
    }

    @Test
    void blacklistedTokenDoesNotSetAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer blacklisted-jti");
        when(jwtService.isTokenValid("blacklisted-jti")).thenReturn(true);
        when(jwtService.extractTokenId("blacklisted-jti")).thenReturn("jti-blacklisted");
        when(jwtService.extractUsername("blacklisted-jti")).thenReturn("user1");
        when(jwtBlacklistRepository.exists("jti-blacklisted")).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(auditService).recordAuthEvent(eq("user1"), eq("JWT_BLACKLISTED"), eq(false), anyString());
    }

    @Test
    void revokedSubjectDoesNotSetAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer revoked-subject");
        when(jwtService.isTokenValid("revoked-subject")).thenReturn(true);
        when(jwtService.extractTokenId("revoked-subject")).thenReturn("jti-revoked");
        when(jwtService.extractUsername("revoked-subject")).thenReturn("revoked-user");
        when(jwtBlacklistRepository.exists("jti-revoked")).thenReturn(false);
        when(jwtBlacklistRepository.isSubjectRevoked("revoked-user")).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(auditService).recordAuthEvent(eq("revoked-user"), eq("JWT_BLACKLISTED"), eq(false), anyString());
    }

    @Test
    void tokenWithTenantsClaimSetsDetails() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer tenants-token");
        when(jwtService.isTokenValid("tenants-token")).thenReturn(true);
        when(jwtService.extractTokenId("tenants-token")).thenReturn("jti-tenants");
        when(jwtService.extractUsername("tenants-token")).thenReturn("scoped-user");
        when(jwtService.extractAuthorities("tenants-token")).thenReturn(List.of("ROLE_USER"));
        when(jwtService.extractTenants("tenants-token")).thenReturn("tenant1,tenant2");
        when(jwtBlacklistRepository.exists("jti-tenants")).thenReturn(false);
        when(jwtBlacklistRepository.isSubjectRevoked("scoped-user")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertEquals("tenant1,tenant2", SecurityContextHolder.getContext().getAuthentication().getDetails());
    }
}
