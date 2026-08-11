package com.insurance.platform.document.service;

import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import com.insurance.platform.document.DocumentIndexStatus;
import com.insurance.platform.document.entity.KnowledgeDocumentEntity;
import com.insurance.platform.document.mapper.KnowledgeDocumentMapper;
import com.insurance.platform.document.storage.StoredDocument;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentPersistenceService {
    private final KnowledgeDocumentMapper mapper;

    public DocumentPersistenceService(KnowledgeDocumentMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public KnowledgeDocumentEntity createUploaded(
            UUID documentId, UUID indexRequestId, long userId, StoredDocument stored) {
        LocalDateTime now = now();
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setDocumentId(documentId.toString());
        entity.setIndexRequestId(indexRequestId.toString());
        entity.setUserId(userId);
        entity.setOriginalFilename(stored.originalFilename());
        entity.setStorageKey(stored.storageKey());
        entity.setContentType(stored.contentType());
        entity.setSizeBytes(stored.sizeBytes());
        entity.setSha256(stored.sha256());
        entity.setIndexStatus(DocumentIndexStatus.UPLOADED.name());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        mapper.insert(entity);
        return entity;
    }

    @Transactional
    public void markIndexing(UUID documentId) {
        transition(documentId, DocumentIndexStatus.UPLOADED,
                DocumentIndexStatus.INDEXING, null);
    }

    @Transactional
    public void markIndexed(UUID documentId) {
        transition(documentId, DocumentIndexStatus.INDEXING,
                DocumentIndexStatus.INDEXED, null);
    }

    @Transactional
    public void markFailure(
            UUID documentId, DocumentIndexStatus status, ErrorCode errorCode) {
        if (status != DocumentIndexStatus.FAILED && status != DocumentIndexStatus.UNKNOWN) {
            throw new IllegalArgumentException("failure status must be terminal");
        }
        transition(documentId, DocumentIndexStatus.INDEXING, status, errorCode);
    }

    private void transition(
            UUID documentId,
            DocumentIndexStatus expected,
            DocumentIndexStatus next,
            ErrorCode errorCode) {
        int changed = mapper.transition(
                documentId.toString(), expected.name(), next.name(),
                errorCode == null ? null : errorCode.name(),
                errorCode == null ? null : errorCode.defaultMessage(), now());
        if (changed != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }
}
