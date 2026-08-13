package com.insurance.platform.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "insurance.knowledge-client")
public record KnowledgeClientProperties(Duration readTimeout) {
    public KnowledgeClientProperties {
        Objects.requireNonNull(readTimeout, "readTimeout must not be null");
        if (readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("readTimeout must be positive");
        }
    }
}
