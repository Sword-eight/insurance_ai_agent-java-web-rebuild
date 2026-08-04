package com.insurance.platform.client.dto;

public record InternalEnvelope<T>(
        boolean success,
        T data,
        InternalError error,
        String traceId) {
}
