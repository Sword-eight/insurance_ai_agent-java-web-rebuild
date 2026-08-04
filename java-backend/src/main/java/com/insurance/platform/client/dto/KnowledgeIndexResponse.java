package com.insurance.platform.client.dto;

import java.util.UUID;

public record KnowledgeIndexResponse(
        UUID requestId,
        UUID documentId,
        String indexStatus) {
}
