package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.zhengyan.ontology.platform.controller.HealthController;
import org.zhengyan.ontology.platform.engine.EngineRegistry;
import org.zhengyan.ontology.platform.engine.OntologyEngine;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
public class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EngineRegistry engineRegistry;

    @MockitoBean
    private OntologyEngine ontologyEngine;

    @Test
    void testHealth() throws Exception {
        given(engineRegistry.getAllEngineIds()).willReturn(List.of("sample", "university"));
        given(engineRegistry.get("sample")).willReturn(ontologyEngine);
        given(engineRegistry.get("university")).willReturn(ontologyEngine);
        given(ontologyEngine.checkHealth()).willReturn("UP");

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.engines.sample").value("UP"));
    }
}
