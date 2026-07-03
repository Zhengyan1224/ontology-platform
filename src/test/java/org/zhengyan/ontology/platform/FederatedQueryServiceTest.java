package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.zhengyan.ontology.platform.config.FederatedQueryProperties;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.zhengyan.ontology.platform.service.FederatedQueryService;
import org.zhengyan.ontology.platform.service.MetricsService;
import org.zhengyan.ontology.platform.service.TenantAccessEvaluator;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FederatedQueryServiceTest {

    @Mock
    private EngineRegistry engineRegistry;

    @Mock
    private MetricsService metricsService;

    private final TenantAccessEvaluator tenantAccessEvaluator = new TenantAccessEvaluator();

    private FederatedQueryProperties props;
    private FederatedQueryService service;

    @BeforeEach
    void setUp() {
        props = new FederatedQueryProperties();
        props.setTimeoutMs(5000);
        props.setPerSubqueryTimeoutMs(2000);
        props.setMaxConcurrency(4);
        service = new FederatedQueryService(engineRegistry, props, tenantAccessEvaluator, metricsService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void containsServiceClauseReturnsTrueWhenSparqlHasServiceClause() {
        String sparql = "SELECT ?s WHERE { SERVICE <tenant:other> { ?s a ?o } }";
        assertTrue(service.containsServiceClause(sparql));
    }

    @Test
    void containsServiceClauseReturnsFalseForNormalSparql() {
        String sparql = "SELECT ?s WHERE { ?s a ?o }";
        assertFalse(service.containsServiceClause(sparql));
    }

    @Test
    void executeFederatedWithoutServiceDelegatesToLocalEngine() throws Exception {
        OntologyEngine mockEngine = mock(OntologyEngine.class);
        SparqlQueryResult expected = new SparqlQueryResult(
                List.of("s"), List.of(Map.of("s", "test")), 5);
        given(engineRegistry.get("tenant1")).willReturn(mockEngine);
        given(mockEngine.executeQuery("SELECT ?s WHERE { ?s a ?o }")).willReturn(expected);

        SparqlQueryResult result = service.executeFederated("tenant1", "SELECT ?s WHERE { ?s a ?o }");
        assertNotNull(result);
        assertEquals("s", result.getVariables().get(0));
    }

    @Test
    void rbacCheckThrowsSecurityExceptionWhenNoAuthentication() {
        SecurityContextHolder.clearContext();
        String federatedSparql = "SELECT ?s WHERE { SERVICE <tenant:other> { ?s a ?o } }";
        assertThrows(SecurityException.class,
                () -> service.executeFederated("tenant1", federatedSparql));
    }

    @Test
    void rbacCheckDeniesAccessToSourceTenant() {
        setAuth("ROLE_READONLY", "university");

        String federatedSparql = "SELECT ?s WHERE { SERVICE <tenant:university> { ?s a ?o } }";
        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.executeFederated("tenant1", federatedSparql));
        assertTrue(ex.getMessage().contains("tenant1"));
    }

    @Test
    void rbacCheckDeniesAccessToFederatedTenant() throws Exception {
        setAuth("ROLE_READONLY", "tenant1");

        String federatedSparql = "SELECT ?s WHERE { SERVICE <tenant:university> { ?s a ?o } }";
        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.executeFederated("tenant1", federatedSparql));
        assertTrue(ex.getMessage().contains("university"));
    }

    @Test
    void rbacCheckAllowsAccessWhenBothTenantsAreAuthorized() throws Exception {
        setAuth("ROLE_READONLY", "tenant1,university");

        OntologyEngine sourceEngine = mock(OntologyEngine.class);
        OntologyEngine targetEngine = mock(OntologyEngine.class);
        given(engineRegistry.get("tenant1")).willReturn(sourceEngine);
        given(engineRegistry.get("university")).willReturn(targetEngine);

        SparqlQueryResult subResult = new SparqlQueryResult(
                List.of("s"), List.of(Map.of("s", "uri:res")), 1);
        given(targetEngine.executeQuery("?s a ?o")).willReturn(subResult);

        SparqlQueryResult mainResult = new SparqlQueryResult(
                List.of("s"), List.of(Map.of("s", "final")), 1);
        given(sourceEngine.executeQuery(anyString())).willReturn(mainResult);

        SparqlQueryResult result = service.executeFederated(
                "tenant1", "SELECT ?s WHERE { SERVICE <tenant:university> { ?s a ?o } }");
        assertNotNull(result);
    }

    @Test
    void adminBypassesTenantScopes() throws Exception {
        setAuth("ROLE_ADMIN", null);

        OntologyEngine sourceEngine = mock(OntologyEngine.class);
        OntologyEngine targetEngine = mock(OntologyEngine.class);
        given(engineRegistry.get("tenant1")).willReturn(sourceEngine);
        given(engineRegistry.get("university")).willReturn(targetEngine);

        SparqlQueryResult subResult = new SparqlQueryResult(
                List.of("s"), List.of(Map.of("s", "uri:res")), 1);
        given(targetEngine.executeQuery("?s a ?o")).willReturn(subResult);

        SparqlQueryResult mainResult = new SparqlQueryResult(
                List.of("s"), List.of(Map.of("s", "final")), 1);
        given(sourceEngine.executeQuery(anyString())).willReturn(mainResult);

        SparqlQueryResult result = service.executeFederated(
                "tenant1", "SELECT ?s WHERE { SERVICE <tenant:university> { ?s a ?o } }");
        assertNotNull(result);
    }

    @Test
    void federatedQueryRecordsMetricsOnSuccess() throws Exception {
        setAuth("ROLE_ADMIN", null);

        OntologyEngine sourceEngine = mock(OntologyEngine.class);
        OntologyEngine targetEngine = mock(OntologyEngine.class);
        given(engineRegistry.get("tenant1")).willReturn(sourceEngine);
        given(engineRegistry.get("university")).willReturn(targetEngine);

        SparqlQueryResult subResult = new SparqlQueryResult(
                List.of("s"), List.of(Map.of("s", "uri:res")), 1);
        given(targetEngine.executeQuery("?s a ?o")).willReturn(subResult);

        SparqlQueryResult mainResult = new SparqlQueryResult(
                List.of("s"), List.of(Map.of("s", "final")), 1);
        given(sourceEngine.executeQuery(anyString())).willReturn(mainResult);

        service.executeFederated("tenant1",
                "SELECT ?s WHERE { SERVICE <tenant:university> { ?s a ?o } }");

        verify(metricsService).recordQuery(eq("tenant1.federated"), anyLong(), eq(true));
    }

    @Test
    void federatedQueryRecordsMetricsOnFailure() {
        SecurityContextHolder.clearContext();
        String sparql = "SELECT ?s WHERE { SERVICE <tenant:university> { ?s a ?o } }";

        assertThrows(SecurityException.class,
                () -> service.executeFederated("tenant1", sparql));
        verify(metricsService).recordQuery(eq("tenant1.federated"), anyLong(), eq(false));
    }

    private static void setAuth(String role, String scopes) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority(role)));
        token.setDetails(scopes);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

}
