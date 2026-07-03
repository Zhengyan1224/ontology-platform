package org.zhengyan.ontology.platform.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TenantAccessEvaluator {

    private static final String WILDCARD = "*";

    public boolean hasAccess(Authentication authentication, String tenantId) {
        if (authentication == null) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        if (isAdmin) {
            return true;
        }

        String details = authentication.getDetails() instanceof String
                ? (String) authentication.getDetails()
                : null;

        if (details == null || details.isBlank() || WILDCARD.equals(details.trim())) {
            return true;
        }

        Set<String> scopes = Arrays.stream(details.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        return scopes.contains(tenantId);
    }
}
