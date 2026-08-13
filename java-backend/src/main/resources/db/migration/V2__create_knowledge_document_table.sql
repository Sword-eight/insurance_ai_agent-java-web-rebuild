CREATE TABLE iap_knowledge_document (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    document_id CHAR(36) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    index_request_id CHAR(36) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    sha256 CHAR(64) NOT NULL,
    index_status VARCHAR(16) NOT NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_document_id UNIQUE (document_id),
    CONSTRAINT uk_document_index_request UNIQUE (index_request_id),
    CONSTRAINT uk_document_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_document_user FOREIGN KEY (user_id) REFERENCES iap_user(id)
);
CREATE INDEX idx_document_user_created
    ON iap_knowledge_document(user_id, created_at DESC);
CREATE INDEX idx_document_status_updated
    ON iap_knowledge_document(index_status, updated_at);
