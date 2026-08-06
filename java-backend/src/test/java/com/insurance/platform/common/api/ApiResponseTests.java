package com.insurance.platform.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiResponseTests {

    @Test
    void successUsesFrozenEnvelopeContract() {
        Instant before = Instant.now();

        ApiResponse<Map<String, String>> response = ApiResponse.success(
                Map.of("status", "UP"),
                "01J4EXAMPLETRACE01");

        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.message()).isEqualTo("success");
        assertThat(response.data()).containsEntry("status", "UP");
        assertThat(response.traceId()).isEqualTo("01J4EXAMPLETRACE01");
        assertThat(response.timestamp()).isBetween(before, Instant.now());
    }

    @Test
    void failureUsesErrorCodeDefaultWithoutExposingInternalData() {
        ApiResponse<Void> response = ApiResponse.failure(
                ErrorCode.INTERNAL_ERROR,
                " ",
                "01J4EXAMPLETRACE01");

        assertThat(response.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.message()).isEqualTo("internal server error");
        assertThat(response.data()).isNull();
    }

    @Test
    void frozenErrorCodesKeepTheirHttpSemantics() {
        assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name))
                .containsExactly(
                        "VALIDATION_ERROR",
                        "AUTH_UNAUTHORIZED",
                        "AUTH_FORBIDDEN",
                        "CONVERSATION_ACCESS_DENIED",
                        "CONVERSATION_NOT_FOUND",
                        "DOCUMENT_NOT_FOUND",
                        "CHAT_IDEMPOTENCY_CONFLICT",
                        "CHAT_REQUEST_IN_PROGRESS",
                        "FILE_TOO_LARGE",
                        "UNSUPPORTED_MEDIA_TYPE",
                        "RATE_LIMIT_EXCEEDED",
                        "RATE_LIMIT_SERVICE_UNAVAILABLE",
                        "AI_EXECUTION_FAILED",
                        "DOCUMENT_INDEX_FAILED",
                        "AI_SERVICE_UNAVAILABLE",
                        "AI_SERVICE_TIMEOUT",
                        "INTERNAL_ERROR");
        assertThat(ErrorCode.VALIDATION_ERROR.httpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.CHAT_IDEMPOTENCY_CONFLICT.httpStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.AI_EXECUTION_FAILED.httpStatus())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ErrorCode.AI_SERVICE_UNAVAILABLE.httpStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ErrorCode.AI_SERVICE_TIMEOUT.httpStatus())
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void businessExceptionFallsBackToSafeDefaultMessage() {
        BusinessException exception = new BusinessException(
                ErrorCode.CONVERSATION_NOT_FOUND);

        assertThat(exception.errorCode())
                .isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("conversation was not found");
    }
}
