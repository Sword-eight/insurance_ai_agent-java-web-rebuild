CREATE TABLE iap_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id CHAR(36) NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_user_id UNIQUE (user_id),
    CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE iap_conversation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id CHAR(36) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(100) NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_conversation_id UNIQUE (conversation_id),
    CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES iap_user(id)
);
CREATE INDEX idx_conversation_user_updated
    ON iap_conversation(user_id, updated_at DESC);

CREATE TABLE iap_chat_request (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id CHAR(36) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    conversation_id BIGINT UNSIGNED NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    sources_json JSON NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_request_id UNIQUE (request_id),
    CONSTRAINT uk_chat_request_user_idem UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_chat_request_user FOREIGN KEY (user_id) REFERENCES iap_user(id),
    CONSTRAINT fk_chat_request_conversation FOREIGN KEY (conversation_id) REFERENCES iap_conversation(id)
);
CREATE INDEX idx_chat_request_conversation_created
    ON iap_chat_request(conversation_id, created_at);

CREATE TABLE iap_chat_message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_id CHAR(36) NOT NULL,
    conversation_id BIGINT UNSIGNED NOT NULL,
    chat_request_id BIGINT UNSIGNED NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    sequence_no BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_message_id UNIQUE (message_id),
    CONSTRAINT uk_chat_message_request_role UNIQUE (chat_request_id, role),
    CONSTRAINT uk_chat_message_conversation_seq UNIQUE (conversation_id, sequence_no),
    CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES iap_conversation(id),
    CONSTRAINT fk_chat_message_request FOREIGN KEY (chat_request_id) REFERENCES iap_chat_request(id)
);
CREATE INDEX idx_chat_message_conversation_created
    ON iap_chat_message(conversation_id, created_at);
