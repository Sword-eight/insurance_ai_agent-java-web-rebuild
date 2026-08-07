package com.insurance.platform.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Java 调 Python AI Service 的进程级配置。 */
@ConfigurationProperties(prefix = "insurance.ai-service")
public record AiServiceProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout) {

    public AiServiceProperties {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
