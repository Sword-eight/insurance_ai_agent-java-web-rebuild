package com.insurance.platform.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "insurance.resilience")
public record AiResilienceProperties(
        int slidingWindowSize,
        int minimumNumberOfCalls,
        float failureRateThreshold,
        Duration waitDurationInOpenState,
        int permittedCallsInHalfOpenState) {

    public AiResilienceProperties {
        if (slidingWindowSize < 1) throw new IllegalArgumentException("slidingWindowSize must be positive");
        if (minimumNumberOfCalls < 1 || minimumNumberOfCalls > slidingWindowSize) {
            throw new IllegalArgumentException("minimumNumberOfCalls is invalid");
        }
        if (failureRateThreshold <= 0 || failureRateThreshold > 100) {
            throw new IllegalArgumentException("failureRateThreshold is invalid");
        }
        Objects.requireNonNull(waitDurationInOpenState, "waitDurationInOpenState must not be null");
        if (waitDurationInOpenState.isZero() || waitDurationInOpenState.isNegative()) {
            throw new IllegalArgumentException("waitDurationInOpenState must be positive");
        }
        if (permittedCallsInHalfOpenState < 1) {
            throw new IllegalArgumentException("permittedCallsInHalfOpenState must be positive");
        }
    }
}
