package com.insurance.platform.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.insurance.platform.chat.cache.CachedMessageSummary;
import com.insurance.platform.chat.cache.RecentMessageCache;
import com.insurance.platform.chat.persistence.ChatPersistenceService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultChatHistoryServiceTests {

    private RecentMessageCache cache;
    private ChatPersistenceService persistence;
    private DefaultChatHistoryService service;
    private UUID conversationId;
    private List<CachedMessageSummary> summaries;

    @BeforeEach
    void setUp() {
        cache = mock(RecentMessageCache.class);
        persistence = mock(ChatPersistenceService.class);
        service = new DefaultChatHistoryService(cache, persistence);
        conversationId = UUID.randomUUID();
        summaries = List.of(
                new CachedMessageSummary(
                        UUID.randomUUID(), UUID.randomUUID(), "USER", "question", 1),
                new CachedMessageSummary(
                        UUID.randomUUID(), UUID.randomUUID(), "ASSISTANT", "answer", 2));
    }

    @Test
    void cacheHitDoesNotQueryMysql() {
        when(cache.find(conversationId)).thenReturn(Optional.of(summaries));

        assertThat(service.load(UUID.randomUUID(), conversationId, 20, 30))
                .extracting("role", "content")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("user", "question"),
                        org.assertj.core.groups.Tuple.tuple("assistant", "answer"));
        verify(persistence, never()).loadRecentMessages(20, 30);
    }

    @Test
    void cacheMissReadsMysqlAndFillsCache() {
        when(cache.find(conversationId)).thenReturn(Optional.empty());
        when(persistence.loadRecentMessages(20, 30)).thenReturn(summaries);

        assertThat(service.load(UUID.randomUUID(), conversationId, 20, 30)).hasSize(2);

        verify(persistence).loadRecentMessages(20, 30);
        verify(cache).put(conversationId, summaries);
    }
}
