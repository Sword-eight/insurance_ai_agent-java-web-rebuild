package com.insurance.platform.common.exception;

import com.insurance.platform.common.api.ApiResponse;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.trace.TraceIdContext;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Java 公共 API 的异常翻译边界。
 *
 * <p>只把稳定、安全信息返回调用方；未知异常堆栈仅记录在服务端日志。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(
            RateLimitExceededException exception) {
        TraceIdContext.markErrorCode(exception.errorCode().name());
        String traceId = TraceIdContext.currentTraceId();
        return ResponseEntity
                .status(exception.errorCode().httpStatus())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
                .body(ApiResponse.failure(
                        exception.errorCode(), exception.getMessage(), traceId));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception) {
        return response(exception.errorCode(), exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            Exception exception) {
        return response(ErrorCode.VALIDATION_ERROR, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception) {
        return response(ErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileTooLarge(
            MaxUploadSizeExceededException exception) {
        return response(ErrorCode.FILE_TOO_LARGE, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception) {
        TraceIdContext.markErrorCode(ErrorCode.INTERNAL_ERROR.name());
        String traceId = TraceIdContext.currentTraceId();
        logger.error(
                "Unhandled public API exception, traceId={}, exceptionType={}",
                traceId,
                exception.getClass().getName());
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.failure(
                        ErrorCode.INTERNAL_ERROR,
                        null,
                        traceId));
    }

    private ResponseEntity<ApiResponse<Void>> response(
            ErrorCode errorCode,
            String safeMessage) {
        TraceIdContext.markErrorCode(errorCode.name());
        String traceId = TraceIdContext.currentTraceId();
        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(ApiResponse.failure(errorCode, safeMessage, traceId));
    }
}
