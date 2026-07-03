package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.zhengyan.ontology.platform.controller.NlqController;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.zhengyan.ontology.platform.service.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NlqController.class)
@AutoConfigureMockMvc(addFilters = false)
public class NlqControllerTest {

    private static final String LIST_ALL = "list all";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NaturalLanguageQueryService nlqService;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private MetricsService metricsService;

    @Test
    void testBlockingQuery() throws Exception {
        given(nlqService.answer(eq("test"), eq(LIST_ALL), any()))
                .willReturn(new NlqResult(LIST_ALL, "SELECT ?x WHERE {?x a :Test}",
                        "template", List.of("x"), List.of(Map.of("x", "v1")), 5));

        mockMvc.perform(post("/api/v1/tenants/test/nlq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + LIST_ALL + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("template"))
                .andExpect(jsonPath("$.question").value(LIST_ALL))
                .andExpect(jsonPath("$.variables[0]").value("x"));
    }

    @Test
    void testStreamQueryStartsAsync() throws Exception {
        doAnswer(invocation -> {
            SseEmitter emitter = invocation.getArgument(3);
            CompletableFuture.runAsync(() -> {
                try {
                    emitter.send(SseEmitter.event().name("status").data(Map.of("stage", "translating")));
                    emitter.send(SseEmitter.event().name("sparql").data(Map.of("sparql", "SELECT ?x WHERE {?x a :Test}")));
                    emitter.send(SseEmitter.event().name("complete").data(Map.of()));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            });
            return null;
        }).when(nlqService).streamAnswer(eq("test"), eq(LIST_ALL), any(), any(SseEmitter.class));

        MvcResult result = mockMvc.perform(get("/api/v1/tenants/test/nlq/stream")
                        .param("question", LIST_ALL))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    void testStreamQueryWithSessionId() throws Exception {
        doAnswer(invocation -> {
            SseEmitter emitter = invocation.getArgument(3);
            CompletableFuture.runAsync(() -> {
                try {
                    emitter.send(SseEmitter.event().name("status").data(Map.of("stage", "translating")));
                    emitter.send(SseEmitter.event().name("complete").data(Map.of()));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            });
            return null;
        }).when(nlqService).streamAnswer(eq("test"), eq(LIST_ALL), eq("session-1"), any(SseEmitter.class));

        MvcResult result = mockMvc.perform(get("/api/v1/tenants/test/nlq/stream")
                        .param("question", LIST_ALL)
                        .param("sessionId", "session-1"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }
}
