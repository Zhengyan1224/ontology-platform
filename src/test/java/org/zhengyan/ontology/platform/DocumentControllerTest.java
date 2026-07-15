package org.zhengyan.ontology.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.zhengyan.ontology.platform.controller.DocumentController;
import org.zhengyan.ontology.platform.model.DocumentChunk;
import org.zhengyan.ontology.platform.model.Document;
import org.zhengyan.ontology.platform.service.DocumentIngestionService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
public class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocumentIngestionService documentIngestionService;

    @Test
    void testListDocuments() throws Exception {
        Document doc = new Document();
        doc.setId("doc-1");
        doc.setTenantId("test");
        doc.setName("Test Doc");
        doc.setStatus("READY");
        doc.setChunkCount(3);

        given(documentIngestionService.listDocuments("test")).willReturn(List.of(doc));

        mockMvc.perform(get("/api/v1/tenants/test/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("doc-1"))
                .andExpect(jsonPath("$[0].name").value("Test Doc"));
    }

    @Test
    void testListDocumentsEmpty() throws Exception {
        given(documentIngestionService.listDocuments("test")).willReturn(List.of());

        mockMvc.perform(get("/api/v1/tenants/test/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetDocument() throws Exception {
        Document doc = new Document();
        doc.setId("doc-1");
        doc.setTenantId("test");
        doc.setName("Test Doc");

        given(documentIngestionService.getDocument("test", "doc-1")).willReturn(doc);

        mockMvc.perform(get("/api/v1/tenants/test/documents/doc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("doc-1"));
    }

    @Test
    void testGetDocumentNotFound() throws Exception {
        given(documentIngestionService.getDocument("test", "nonexistent")).willReturn(null);

        mockMvc.perform(get("/api/v1/tenants/test/documents/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUploadDocument() throws Exception {
        Document doc = new Document();
        doc.setId("doc-uploaded");
        doc.setTenantId("test");
        doc.setName("test.txt");
        doc.setStatus("READY");
        doc.setChunkCount(2);
        doc.setContentType("text/plain");
        doc.setFileSize(27);

        given(documentIngestionService.uploadDocument(eq("test"), any(), any()))
                .willReturn(doc);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "Hello world test content".getBytes());

        mockMvc.perform(multipart("/api/v1/tenants/test/documents/upload")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("doc-uploaded"))
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void testUploadDocumentEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/v1/tenants/test/documents/upload")
                        .file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDeleteDocument() throws Exception {
        given(documentIngestionService.deleteDocument("test", "doc-1")).willReturn(true);

        mockMvc.perform(delete("/api/v1/tenants/test/documents/doc-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteDocumentNotFound() throws Exception {
        given(documentIngestionService.deleteDocument("test", "nonexistent")).willReturn(false);

        mockMvc.perform(delete("/api/v1/tenants/test/documents/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testQueryDocuments() throws Exception {
        var result = new DocumentIngestionService.DocumentQueryResult(
                "chunk-1", "doc-1", 0, "test content", 0.95);

        given(documentIngestionService.queryDocuments(eq("test"), eq("test query"), eq(5)))
                .willReturn(List.of(result));

        mockMvc.perform(post("/api/v1/tenants/test/documents/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("query", "test query", "topK", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chunkId").value("chunk-1"))
                .andExpect(jsonPath("$[0].score").value(0.95));
    }

    @Test
    void testQueryDocumentsEmptyQuery() throws Exception {
        mockMvc.perform(post("/api/v1/tenants/test/documents/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("query", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetChunks() throws Exception {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId("chunk-1");
        chunk.setDocumentId("doc-1");
        chunk.setChunkIndex(0);
        chunk.setContent("test chunk");
        chunk.setCreatedAt(LocalDateTime.now());

        given(documentIngestionService.getChunks("test", "doc-1"))
                .willReturn(List.of(chunk));

        mockMvc.perform(get("/api/v1/tenants/test/documents/doc-1/chunks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("chunk-1"))
                .andExpect(jsonPath("$[0].chunkIndex").value(0));
    }
}
