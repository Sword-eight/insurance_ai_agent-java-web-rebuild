package com.insurance.platform.common.exception;

import com.insurance.platform.common.error.ErrorCode;
import java.util.Objects;

/**
 * Service/Client 可抛出的稳定业务异常；HTTP 转换属于 GlobalExceptionHandler。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, String safeMessage) {
        super(resolveMessage(errorCode, safeMessage));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    private static String resolveMessage(ErrorCode errorCode, String safeMessage) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        return safeMessage == null || safeMessage.isBlank()
                ? errorCode.defaultMessage()
                : safeMessage;
    }
}
