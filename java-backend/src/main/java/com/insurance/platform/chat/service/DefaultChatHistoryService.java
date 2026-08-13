package com.insurance.platform.chat.service;

import com.insurance.platform.chat.cache.CachedMessageSummary;
import com.insurance.platform.chat.cache.RecentMessageCache;
import com.insurance.platform.chat.persistence.ChatPersistenceService;
import com.insurance.platform.client.dto.HistoryMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultChatHistoryService implements ChatHistoryService {

    private final RecentMessageCache cache;
    private final ChatPersistenceService persistenceService;

    public DefaultChatHistoryService(
            RecentMessageCache cache,
            ChatPersistenceService persistenceService) {
        this.cache = cache;
        this.persistenceService = persistenceService;
    }

    @Override
    public List<HistoryMessage> load(
            UUID userId,
            UUID conversationId,
            long internalConversationId,
            long currentRequestId) {
        List<CachedMessageSummary> messages = cache.find(conversationId)
                .orElseGet(() -> loadAndCache(conversationId, internalConversationId, currentRequestId));
        return messages.stream()
                .map(message -> new HistoryMessage(
                        message.role().toLowerCase(), message.content()))
                .toList();
    }

    @Override
    public void evict(UUID conversationId) {
        cache.evict(conversationId);
    }

    private List<CachedMessageSummary> loadAndCache(
            UUID conversationId,
            long internalConversationId,
            long currentRequestId) {
        List<CachedMessageSummary> messages = persistenceService.loadRecentMessages(
                internalConversationId, currentRequestId);
        cache.put(conversationId, messages);
        return messages;
    }
}
