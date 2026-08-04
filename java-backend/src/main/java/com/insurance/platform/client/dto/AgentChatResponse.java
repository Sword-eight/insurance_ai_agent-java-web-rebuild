package com.insurance.platform.client.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AgentChatResponse(
        UUID requestId,
        String answer,
        List<Map<String, Object>> sources,
        long durationMs) {

    public AgentChatResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
