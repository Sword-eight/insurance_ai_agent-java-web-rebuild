package com.insurance.platform.chat.persistence;

import com.insurance.platform.chat.vo.ChatResponse;
import java.util.UUID;

public record PreparedChat(
        long internalRequestId,
        long internalConversationId,
        UUID conversationId,
        UUID requestId,
        UUID userMessageId,
        ChatRequestStatus status,
        ChatResponse replayResponse) {

    public boolean isReplay() {
        return replayResponse != null;
    }
}
