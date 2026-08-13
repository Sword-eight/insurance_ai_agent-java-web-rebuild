package com.insurance.platform.chat.cache;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecentMessageCache {

    Optional<List<CachedMessageSummary>> find(UUID conversationId);

    void put(UUID conversationId, List<CachedMessageSummary> messages);

    void evict(UUID conversationId);
}
