package org.zhengyan.ontology.platform.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.zhengyan.ontology.platform.model.ApiKeyEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;
    private final AuditService auditService;
    private final List<String> publicPaths;

    public ApiKeyFilter(ApiKeyService apiKeyService, AuditService auditService, List<String> publicPaths) {
        this.apiKeyService = apiKeyService;
        this.auditService = auditService;
        this.publicPaths = publicPaths;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return publicPaths.stream().anyMatch(p -> path.startsWith(p) || path.matches(p));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<ApiKeyEntity> keyEntity = apiKeyService.validateKey(apiKey);

        if (keyEntity.isEmpty()) {
            auditService.recordAuthEvent(apiKey, "API_KEY_AUTH", false, "Invalid API key");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Missing or invalid API key\"}");
            return;
        }

        ApiKeyEntity entity = keyEntity.get();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        entity.getName(), null,
                        List.of(new SimpleGrantedAuthority(entity.getRole())));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
