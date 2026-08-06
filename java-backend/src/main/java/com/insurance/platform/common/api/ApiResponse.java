package com.insurance.platform.common.api;

import com.insurance.platform.common.error.ErrorCode;
import java.time.Instant;
import java.util.Objects;

/**
 * Phase 2 冻结的 Java 公共 API Envelope。
 *
 * <p>HTTP status 由 Controller 或异常处理器表达，本类型只表达稳定业务 Envelope。
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        Instant timestamp) {

    public ApiResponse {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("OK", "success", data, traceId, Instant.now());
    }

    public static ApiResponse<Void> failure(
            ErrorCode errorCode,
            String safeMessage,
            String traceId) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        String message = safeMessage == null || safeMessage.isBlank()
                ? errorCode.defaultMessage()
                : safeMessage;
        return new ApiResponse<>(
                errorCode.name(),
                message,
                null,
                traceId,
                Instant.now());
    }
}
