package com.insurance.platform.chat.vo;

import java.util.List;
import java.util.UUID;

/** Phase 2 冻结的同步聊天公共响应 VO。 */
public record ChatResponse(
        UUID conversationId,
        UUID requestId,
        UUID userMessageId,
        UUID assistantMessageId,
        String answer,
        List<ChatSource> sources) {

    public ChatResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
