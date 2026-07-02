package org.zhengyan.ontology.platform.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.zhengyan.ontology.platform.repository.JwtBlacklistRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final JwtBlacklistRepository jwtBlacklistRepository;
    private final AuditService auditService;

    public JwtAuthFilter(JwtService jwtService, JwtBlacklistRepository jwtBlacklistRepository, AuditService auditService) {
        this.jwtService = jwtService;
        this.jwtBlacklistRepository = jwtBlacklistRepository;
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());

            if (jwtService.isTokenValid(token)) {
                String jti = jwtService.extractTokenId(token);
                String username = jwtService.extractUsername(token);

                boolean blacklisted = jwtBlacklistRepository.exists(jti)
                        || jwtBlacklistRepository.isSubjectRevoked(username);

                if (blacklisted) {
                    auditService.recordAuthEvent(username, "JWT_BLACKLISTED", false, "Token revoked: jti=" + jti);
                }

                if (!blacklisted) {
                    List<String> authorities = jwtService.extractAuthorities(token);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    username, null,
                                    authorities.stream().map(SimpleGrantedAuthority::new).toList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
