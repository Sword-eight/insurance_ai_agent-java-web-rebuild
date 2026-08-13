package com.insurance.platform.document.storage;

import java.nio.file.Path;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "insurance.document-storage")
public record DocumentStorageProperties(Path root, DataSize maxSize) {
    public DocumentStorageProperties {
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(maxSize, "maxSize must not be null");
        if (maxSize.toBytes() <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
    }
}
