package com.insurance.platform.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "insurance.ai-health")
public record AiHealthProperties(Duration connectTimeout, Duration readTimeout) {
    public AiHealthProperties {
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
    }
}
