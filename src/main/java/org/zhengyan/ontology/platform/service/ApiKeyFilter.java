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

    public ApiKeyFilter(ApiKeyService apiKeyService, AuditService auditService, List<String> publicPaths) {
        this.apiKeyService = apiKeyService;
        this.auditService = auditService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(API_KEY_HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        Optional<ApiKeyEntity> keyEntity = apiKeyService.validateKey(apiKey);

        if (keyEntity.isPresent()) {
            ApiKeyEntity entity = keyEntity.get();
            java.util.Map<String, Object> authDetails = new java.util.LinkedHashMap<>();
            authDetails.put("tenantScopes", entity.getTenantScopes() != null ? entity.getTenantScopes() : "*");
            authDetails.put("apiKeyId", entity.getId());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            entity.getName(), null,
                            List.of(new SimpleGrantedAuthority(entity.getRole())));
            authentication.setDetails(authDetails);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
            auditService.recordAuthEvent(apiKey, "API_KEY_AUTH", false, "Invalid API key");
        }

        filterChain.doFilter(request, response);
    }
}
