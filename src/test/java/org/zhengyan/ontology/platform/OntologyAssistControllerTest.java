package org.zhengyan.ontology.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.zhengyan.ontology.platform.controller.OntologyAssistController;
import org.zhengyan.ontology.platform.model.OntologyProposal;
import org.zhengyan.ontology.platform.service.LlmOntologyAssistService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OntologyAssistController.class)
@AutoConfigureMockMvc(addFilters = false)
public class OntologyAssistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LlmOntologyAssistService assistService;

    @Test
    void testExtract() throws Exception {
        OntologyProposal proposal = new OntologyProposal();
        proposal.setId("prop-1");
        proposal.setTenantId("test");
        proposal.setTitle("Test Domain");
        proposal.setStatus("draft");
        proposal.setSource("llm_extract");

        given(assistService.extractFromDescription(eq("test"), eq("Test Domain"), eq("books and authors")))
                .willReturn(proposal);

        mockMvc.perform(post("/api/v1/tenants/test/ontology-assist/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Test Domain",
                                "description", "books and authors"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("prop-1"))
                .andExpect(jsonPath("$.source").value("llm_extract"));
    }

    @Test
    void testExtractMissingDescription() throws Exception {
        mockMvc.perform(post("/api/v1/tenants/test/ontology-assist/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Test"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testListProposals() throws Exception {
        OntologyProposal p = new OntologyProposal();
        p.setId("prop-1");
        p.setTitle("Test");
        p.setStatus("draft");

        given(assistService.listProposals("test")).willReturn(List.of(p));

        mockMvc.perform(get("/api/v1/tenants/test/ontology-assist/proposals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("prop-1"));
    }

    @Test
    void testGetProposal() throws Exception {
        OntologyProposal p = new OntologyProposal();
        p.setId("prop-1");
        p.setTitle("Test");

        given(assistService.getProposal("test", "prop-1")).willReturn(p);

        mockMvc.perform(get("/api/v1/tenants/test/ontology-assist/proposals/prop-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("prop-1"));
    }

    @Test
    void testGetProposalNotFound() throws Exception {
        given(assistService.getProposal("test", "nonexistent")).willReturn(null);

        mockMvc.perform(get("/api/v1/tenants/test/ontology-assist/proposals/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testApplyProposal() throws Exception {
        OntologyProposal p = new OntologyProposal();
        p.setId("prop-1");
        p.setStatus("applied");

        given(assistService.applyProposal("test", "prop-1")).willReturn(p);

        mockMvc.perform(post("/api/v1/tenants/test/ontology-assist/proposals/prop-1/apply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("applied"));
    }

    @Test
    void testApplyProposalNotFound() throws Exception {
        given(assistService.applyProposal("test", "nonexistent")).willReturn(null);

        mockMvc.perform(post("/api/v1/tenants/test/ontology-assist/proposals/nonexistent/apply"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testApplyProposalAlreadyApplied() throws Exception {
        given(assistService.applyProposal("test", "prop-1"))
                .willThrow(new IllegalStateException("Proposal is already applied"));

        mockMvc.perform(post("/api/v1/tenants/test/ontology-assist/proposals/prop-1/apply"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRejectProposal() throws Exception {
        OntologyProposal p = new OntologyProposal();
        p.setId("prop-1");
        p.setStatus("rejected");
        p.setRejectionReason("not needed");

        given(assistService.rejectProposal("test", "prop-1", "not needed")).willReturn(p);

        mockMvc.perform(post("/api/v1/tenants/test/ontology-assist/proposals/prop-1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "not needed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"));
    }

    @Test
    void testRejectProposalNotFound() throws Exception {
        given(assistService.rejectProposal("test", "nonexistent", "")).willReturn(null);

        mockMvc.perform(post("/api/v1/tenants/test/ontology-assist/proposals/nonexistent/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteProposal() throws Exception {
        given(assistService.deleteProposal("test", "prop-1")).willReturn(true);

        mockMvc.perform(delete("/api/v1/tenants/test/ontology-assist/proposals/prop-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteProposalNotFound() throws Exception {
        given(assistService.deleteProposal("test", "nonexistent")).willReturn(false);

        mockMvc.perform(delete("/api/v1/tenants/test/ontology-assist/proposals/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetDdlHints() throws Exception {
        var hints = new LlmOntologyAssistService.DdlHintsResult(true, null,
                List.of(), "Tables: ...");

        given(assistService.getDdlHints("test")).willReturn(hints);

        mockMvc.perform(get("/api/v1/tenants/test/ontology-assist/ddl-hints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testGetDdlHintsError() throws Exception {
        var hints = new LlmOntologyAssistService.DdlHintsResult(false, "DB error",
                List.of(), null);

        given(assistService.getDdlHints("test")).willReturn(hints);

        mockMvc.perform(get("/api/v1/tenants/test/ontology-assist/ddl-hints"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGenerateFromDdl() throws Exception {
        OntologyProposal proposal = new OntologyProposal();
        proposal.setId("ddl-prop");
        proposal.setSource("ddl_analysis");

        given(assistService.generateFromDdl(eq("test"), eq("My Schema"))).willReturn(proposal);

        mockMvc.perform(post("/api/v1/tenants/test/ontology-assist/generate-from-ddl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "My Schema"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ddl-prop"));
    }
}
