package com.insurance.platform.document.storage;

public record StoredDocument(
        String originalFilename,
        String storageKey,
        String contentType,
        long sizeBytes,
        String sha256) {
}
