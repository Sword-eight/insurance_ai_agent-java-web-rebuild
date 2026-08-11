package com.insurance.platform.document.service;

import com.insurance.platform.client.KnowledgeClient;
import com.insurance.platform.client.dto.KnowledgeIndexMetadata;
import com.insurance.platform.client.exception.KnowledgeClientException;
import com.insurance.platform.common.api.PageResponse;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import com.insurance.platform.document.DocumentIndexStatus;
import com.insurance.platform.document.entity.KnowledgeDocumentEntity;
import com.insurance.platform.document.mapper.KnowledgeDocumentMapper;
import com.insurance.platform.document.storage.DocumentStorage;
import com.insurance.platform.document.storage.StoredDocument;
import com.insurance.platform.document.vo.DocumentView;
import com.insurance.platform.security.CurrentUserProvider;
import com.insurance.platform.user.mapper.UserMapper;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
    private final CurrentUserProvider currentUserProvider;
    private final UserMapper userMapper;
    private final KnowledgeDocumentMapper mapper;
    private final DocumentPersistenceService persistence;
    private final DocumentStorage storage;
    private final KnowledgeClient knowledgeClient;

    public DocumentService(
            CurrentUserProvider currentUserProvider,
            UserMapper userMapper,
            KnowledgeDocumentMapper mapper,
            DocumentPersistenceService persistence,
            DocumentStorage storage,
            KnowledgeClient knowledgeClient) {
        this.currentUserProvider = currentUserProvider;
        this.userMapper = userMapper;
        this.mapper = mapper;
        this.persistence = persistence;
        this.storage = storage;
        this.knowledgeClient = knowledgeClient;
    }

    /** Deliberately non-transactional: the Python HTTP call must stay outside MySQL transactions. */
    public DocumentView upload(MultipartFile file, String traceId) {
        long userId = requireInternalUserId();
        UUID documentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        StoredDocument stored = storage.store(documentId, file);
        KnowledgeDocumentEntity entity;
        try {
            entity = persistence.createUploaded(documentId, requestId, userId, stored);
        } catch (RuntimeException exception) {
            storage.delete(stored.storageKey());
            throw exception;
        }

        persistence.markIndexing(documentId);
        try {
            knowledgeClient.indexDocument(
                    new KnowledgeIndexMetadata(
                            requestId, documentId, stored.originalFilename(), stored.sha256()),
                    storage.load(stored.storageKey()), traceId);
            persistence.markIndexed(documentId);
            entity.setIndexStatus(DocumentIndexStatus.INDEXED.name());
            return toView(entity);
        } catch (KnowledgeClientException exception) {
            BusinessException publicFailure = mapClientFailure(exception);
            persistence.markFailure(
                    documentId,
                    isUnknown(exception) ? DocumentIndexStatus.UNKNOWN : DocumentIndexStatus.FAILED,
                    publicFailure.errorCode());
            throw publicFailure;
        } catch (RuntimeException exception) {
            persistence.markFailure(
                    documentId, DocumentIndexStatus.FAILED, ErrorCode.DOCUMENT_INDEX_FAILED);
            throw new BusinessException(ErrorCode.DOCUMENT_INDEX_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentView> list(int page, int size) {
        long userId = requireInternalUserId();
        long offset = (long) (page - 1) * size;
        return new PageResponse<>(
                mapper.findPage(userId, offset, size).stream()
                        .map(DocumentService::toView).toList(),
                page, size, mapper.countOwned(userId));
    }

    @Transactional(readOnly = true)
    public DocumentView get(UUID documentId) {
        long userId = requireInternalUserId();
        KnowledgeDocumentEntity entity = mapper.findOwned(documentId.toString(), userId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        return toView(entity);
    }

    private long requireInternalUserId() {
        Long internalId = userMapper.findActiveInternalId(
                currentUserProvider.requireUserId().toString());
        if (internalId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        return internalId;
    }

    private static BusinessException mapClientFailure(KnowledgeClientException exception) {
        return switch (exception.kind()) {
            case TIMEOUT -> new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT);
            case UNAVAILABLE, DELIVERY_UNKNOWN ->
                    new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            case REJECTED, PROTOCOL ->
                    new BusinessException(ErrorCode.DOCUMENT_INDEX_FAILED);
        };
    }

    private static boolean isUnknown(KnowledgeClientException exception) {
        return exception.kind() == KnowledgeClientException.Kind.TIMEOUT
                || exception.kind() == KnowledgeClientException.Kind.DELIVERY_UNKNOWN;
    }

    private static DocumentView toView(KnowledgeDocumentEntity entity) {
        return new DocumentView(
                UUID.fromString(entity.getDocumentId()),
                entity.getOriginalFilename(),
                entity.getSizeBytes(),
                entity.getIndexStatus(),
                entity.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
