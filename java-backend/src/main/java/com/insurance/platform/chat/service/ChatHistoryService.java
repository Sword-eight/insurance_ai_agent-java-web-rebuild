package com.insurance.platform.chat.service;

import com.insurance.platform.client.dto.HistoryMessage;
import java.util.List;
import java.util.UUID;

public interface ChatHistoryService {

    List<HistoryMessage> load(
            UUID userId,
            UUID conversationId,
            long internalConversationId,
            long currentRequestId);

    void evict(UUID conversationId);
}
