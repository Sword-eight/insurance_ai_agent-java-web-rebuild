package com.insurance.platform.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.insurance.platform.client.KnowledgeClient;
import com.insurance.platform.client.exception.KnowledgeClientException;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import com.insurance.platform.document.mapper.KnowledgeDocumentMapper;
import com.insurance.platform.document.service.DocumentPersistenceService;
import com.insurance.platform.document.service.DocumentService;
import com.insurance.platform.document.storage.DocumentStorage;
import com.insurance.platform.document.storage.StoredDocument;
import com.insurance.platform.security.CurrentUserProvider;
import com.insurance.platform.user.mapper.UserMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

class DocumentServiceTests {
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
    private final DocumentPersistenceService persistence = mock(DocumentPersistenceService.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final KnowledgeClient client = mock(KnowledgeClient.class);
    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(users, userMapper, mapper, persistence, storage, client);
        UUID userId = UUID.randomUUID();
        when(users.requireUserId()).thenReturn(userId);
        when(userMapper.findActiveInternalId(userId.toString())).thenReturn(7L);
    }

    @Test
    void metadataFailureCompensatesOnlyNewFile() {
        StoredDocument stored = stored();
        when(storage.store(any(), any())).thenReturn(stored);
        when(persistence.createUploaded(any(), any(), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR));

        assertThatThrownBy(() -> service.upload(file(), "trace"))
                .isInstanceOf(BusinessException.class);
        verify(storage).delete(stored.storageKey());
    }

    @Test
    void timeoutBecomesUnknownAndOriginalFileIsRetained() {
        StoredDocument stored = stored();
        var entity = new com.insurance.platform.document.entity.KnowledgeDocumentEntity();
        entity.setDocumentId(UUID.randomUUID().toString());
        when(storage.store(any(), any())).thenReturn(stored);
        when(storage.load(stored.storageKey())).thenReturn(new ByteArrayResource("pdf".getBytes()));
        when(persistence.createUploaded(any(), any(), anyLong(), any())).thenAnswer(invocation -> {
            entity.setDocumentId(invocation.<UUID>getArgument(0).toString());
            return entity;
        });
        when(client.indexDocument(any(), any(), any()))
                .thenThrow(new KnowledgeClientException(KnowledgeClientException.Kind.TIMEOUT, null));

        assertThatThrownBy(() -> service.upload(file(), "trace"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_SERVICE_TIMEOUT));
        verify(persistence).markFailure(
                UUID.fromString(entity.getDocumentId()),
                DocumentIndexStatus.UNKNOWN,
                ErrorCode.AI_SERVICE_TIMEOUT);
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).delete(any());
    }

    private static StoredDocument stored() {
        return new StoredDocument("terms.pdf", UUID.randomUUID() + ".pdf",
                "application/pdf", 10, "a".repeat(64));
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "terms.pdf", "application/pdf", "%PDF-x".getBytes());
    }
}
