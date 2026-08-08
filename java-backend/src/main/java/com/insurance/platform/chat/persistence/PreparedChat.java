package com.insurance.platform.chat.persistence;

import com.insurance.platform.chat.vo.ChatResponse;
import com.insurance.platform.client.dto.HistoryMessage;
import java.util.List;
import java.util.UUID;

public record PreparedChat(
        long internalRequestId,
        long internalConversationId,
        UUID conversationId,
        UUID requestId,
        UUID userMessageId,
        List<HistoryMessage> history,
        ChatResponse replayResponse) {

    public boolean isReplay() {
        return replayResponse != null;
    }
}
