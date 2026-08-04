package com.insurance.platform.client.dto;

public record InternalError(
        String code,
        String type,
        String message,
        boolean retryable) {
}
