package com.insurance.platform.document.vo;

import java.time.Instant;
import java.util.UUID;

public record DocumentView(
        UUID documentId,
        String originalFilename,
        long sizeBytes,
        String indexStatus,
        Instant createdAt) {
}
