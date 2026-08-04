package com.insurance.platform.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record KnowledgeIndexMetadata(
        @NotNull UUID requestId,
        @NotNull UUID documentId,
        @NotBlank @Size(max = 255) String originalFilename,
        @NotBlank @Pattern(regexp = "[A-Fa-f0-9]{64}") String sha256) {

    public KnowledgeIndexMetadata {
        originalFilename = originalFilename == null ? null : originalFilename.trim();
        sha256 = sha256 == null ? null : sha256.toLowerCase();
    }
}
