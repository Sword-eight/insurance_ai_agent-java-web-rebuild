package com.insurance.platform.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.insurance.platform.client.KnowledgeClient;
import com.insurance.platform.client.dto.KnowledgeIndexMetadata;
import com.insurance.platform.client.dto.KnowledgeIndexResponse;
import com.insurance.platform.client.exception.KnowledgeClientException;
import com.insurance.platform.document.storage.DocumentStorageProperties;
import com.insurance.platform.security.CurrentUserProvider;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "00000000-0000-0000-0000-000000000010")
class Phase10DocumentIntegrationTests {
    private static final String TRACE_ID = "01J4EXAMPLETRACE10";
    private static final byte[] PDF = "%PDF-1.7\nphase ten integration".getBytes();

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Autowired DocumentStorageProperties storageProperties;
    @MockBean CurrentUserProvider currentUserProvider;
    @MockBean KnowledgeClient knowledgeClient;

    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM iap_knowledge_document");
        jdbc.update("DELETE FROM iap_chat_message");
        jdbc.update("DELETE FROM iap_chat_request");
        jdbc.update("DELETE FROM iap_conversation");
        jdbc.update("DELETE FROM iap_user");
        reset(currentUserProvider, knowledgeClient);
        userId = insertUser("phase10-user");
        when(currentUserProvider.requireUserId()).thenReturn(userId);
        clearStoredFiles();
    }

    @AfterEach
    void tearDown() throws Exception {
        jdbc.update("DELETE FROM iap_knowledge_document");
        clearStoredFiles();
    }

    @Test
    void uploadListAndOwnedDetailUseFrozenEnvelopeAndNoLongTransaction() throws Exception {
        AtomicBoolean transactionSeen = new AtomicBoolean(true);
        when(knowledgeClient.indexDocument(any(), any(), anyString())).thenAnswer(invocation -> {
            transactionSeen.set(TransactionSynchronizationManager.isActualTransactionActive());
            KnowledgeIndexMetadata metadata = invocation.getArgument(0);
            assertThat(invocation.getArgument(1, org.springframework.core.io.Resource.class)
                    .getInputStream().readNBytes(5)).isEqualTo("%PDF-".getBytes());
            return new KnowledgeIndexResponse(
                    metadata.requestId(), metadata.documentId(), "INDEXED");
        });

        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/documents")
                        .file(pdf("terms.pdf", PDF, "application/pdf"))
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.originalFilename").value("terms.pdf"))
                .andExpect(jsonPath("$.data.indexStatus").value("INDEXED"))
                .andReturn();
        String documentId = com.jayway.jsonpath.JsonPath.read(
                uploaded.getResponse().getContentAsString(), "$.data.documentId");

        assertThat(transactionSeen).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT index_status FROM iap_knowledge_document", String.class))
                .isEqualTo("INDEXED");
        assertThat(storedFileCount()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/documents").header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].documentId").value(documentId));
        mockMvc.perform(get("/api/v1/documents/{id}", documentId)
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indexStatus").value("INDEXED"));

        UUID foreignUser = insertUser("phase10-foreign");
        when(currentUserProvider.requireUserId()).thenReturn(foreignUser);
        mockMvc.perform(get("/api/v1/documents/{id}", documentId)
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void timeoutPersistsUnknownAndKeepsOriginalFile() throws Exception {
        when(knowledgeClient.indexDocument(any(), any(), anyString()))
                .thenThrow(new KnowledgeClientException(
                        KnowledgeClientException.Kind.TIMEOUT, null));

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(pdf("terms.pdf", PDF, "application/pdf"))
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("AI_SERVICE_TIMEOUT"));

        assertThat(jdbc.queryForObject(
                "SELECT index_status FROM iap_knowledge_document", String.class))
                .isEqualTo("UNKNOWN");
        assertThat(storedFileCount()).isEqualTo(1);
    }

    @Test
    void explicitPythonFailurePersistsFailed() throws Exception {
        when(knowledgeClient.indexDocument(any(), any(), anyString()))
                .thenThrow(new KnowledgeClientException(
                        KnowledgeClientException.Kind.REJECTED,
                        "KNOWLEDGE_INDEX_FAILED"));

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(pdf("terms.pdf", PDF, "application/pdf"))
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("DOCUMENT_INDEX_FAILED"));
        assertThat(jdbc.queryForObject(
                "SELECT index_status FROM iap_knowledge_document", String.class))
                .isEqualTo("FAILED");
    }

    @Test
    void invalidPdfIsRejectedBeforePersistenceOrPython() throws Exception {
        mockMvc.perform(multipart("/api/v1/documents")
                        .file(pdf("terms.pdf", "not-pdf".getBytes(), "application/pdf"))
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iap_knowledge_document", Long.class)).isZero();
        verifyNoInteractions(knowledgeClient);
    }

    @Test
    void fileOverTwentyMiBReturnsFrozen413BeforePersistenceOrPython() throws Exception {
        byte[] oversized = new byte[20 * 1024 * 1024 + 1];
        System.arraycopy("%PDF-".getBytes(), 0, oversized, 0, 5);

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(pdf("oversized.pdf", oversized, "application/pdf"))
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iap_knowledge_document", Long.class)).isZero();
        verifyNoInteractions(knowledgeClient);
    }

    private UUID insertUser(String username) {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO iap_user
                    (user_id, username, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, id.toString(), username, "hash", now, now);
        return id;
    }

    private static MockMultipartFile pdf(String name, byte[] bytes, String type) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    private void clearStoredFiles() throws Exception {
        var root = storageProperties.root().toAbsolutePath().normalize();
        Files.createDirectories(root);
        try (var files = Files.list(root)) {
            for (var file : files.toList()) Files.deleteIfExists(file);
        }
    }

    private long storedFileCount() throws Exception {
        try (var files = Files.list(storageProperties.root())) {
            return files.count();
        }
    }
}
