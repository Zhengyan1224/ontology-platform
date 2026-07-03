package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.zhengyan.ontology.platform.service.TenantAccessEvaluator;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TenantAccessEvaluatorTest {

    private final TenantAccessEvaluator evaluator = new TenantAccessEvaluator();

    @Test
    void adminHasAccessToAnyTenant() {
        Authentication auth = mock();
        when(auth.getAuthorities()).thenReturn(authList("ROLE_ADMIN"));

        assertTrue(evaluator.hasAccess(auth, "sample"));
        assertTrue(evaluator.hasAccess(auth, "university"));
        assertTrue(evaluator.hasAccess(auth, "nonexistent"));
    }

    @Test
    void wildcardScopeAllowsAnyTenant() {
        Authentication auth = mock();
        when(auth.getAuthorities()).thenReturn(authList("ROLE_READONLY"));
        when(auth.getDetails()).thenReturn("*");

        assertTrue(evaluator.hasAccess(auth, "sample"));
        assertTrue(evaluator.hasAccess(auth, "university"));
    }

    @Test
    void nullDetailsDefaultsToAllTenants() {
        Authentication auth = mock();
        when(auth.getAuthorities()).thenReturn(authList("ROLE_READONLY"));
        when(auth.getDetails()).thenReturn(null);

        assertTrue(evaluator.hasAccess(auth, "sample"));
    }

    @Test
    void scopedKeyDeniesAccessToOtherTenant() {
        Authentication auth = mock();
        when(auth.getAuthorities()).thenReturn(authList("ROLE_READONLY"));
        when(auth.getDetails()).thenReturn("sample");

        assertTrue(evaluator.hasAccess(auth, "sample"));
        assertFalse(evaluator.hasAccess(auth, "university"));
    }

    @Test
    void scopedKeyWithMultipleTenants() {
        Authentication auth = mock();
        when(auth.getAuthorities()).thenReturn(authList("ROLE_READONLY"));
        when(auth.getDetails()).thenReturn("sample,university");

        assertTrue(evaluator.hasAccess(auth, "sample"));
        assertTrue(evaluator.hasAccess(auth, "university"));
        assertFalse(evaluator.hasAccess(auth, "other"));
    }

    @Test
    void nullAuthenticationReturnsFalse() {
        assertFalse(evaluator.hasAccess(null, "sample"));
    }

    private static Collection authList(String role) {
        return List.of(new SimpleGrantedAuthority(role));
    }
}
