package com.insurance.platform.common.error;

import org.springframework.http.HttpStatus;

/**
 * Phase 2 冻结的 Java 公共错误码与 HTTP 状态映射。
 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "request validation failed"),
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "authentication is required"),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "access is forbidden"),
    CONVERSATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "conversation access is denied"),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "conversation was not found"),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "document was not found"),
    CHAT_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "idempotency key conflicts with another request"),
    CHAT_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "chat request is already in progress"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "uploaded file is too large"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported media type"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "rate limit exceeded"),
    RATE_LIMIT_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "rate limit service is unavailable"),
    AI_EXECUTION_FAILED(HttpStatus.BAD_GATEWAY, "AI execution failed"),
    DOCUMENT_INDEX_FAILED(HttpStatus.BAD_GATEWAY, "document indexing failed"),
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI service is unavailable"),
    AI_SERVICE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI service timed out"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal server error");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
