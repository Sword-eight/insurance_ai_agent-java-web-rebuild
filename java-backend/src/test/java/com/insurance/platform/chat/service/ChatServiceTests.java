package com.insurance.platform.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

import com.insurance.platform.chat.dto.ChatRequest;
import com.insurance.platform.chat.persistence.ChatPersistenceService;
import com.insurance.platform.chat.persistence.ChatRequestStatus;
import com.insurance.platform.chat.persistence.PreparedChat;
import com.insurance.platform.chat.idempotency.ChatIdempotencyCache;
import com.insurance.platform.chat.idempotency.IdempotencySummary;
import com.insurance.platform.chat.ratelimit.ChatRateLimiter;
import com.insurance.platform.chat.ratelimit.RateLimitDecision;
import com.insurance.platform.chat.vo.ChatResponse;
import com.insurance.platform.client.AgentClient;
import com.insurance.platform.client.dto.AgentChatResponse;
import com.insurance.platform.client.exception.AgentClientException;
import com.insurance.platform.client.exception.AgentClientException.Kind;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import com.insurance.platform.common.exception.RateLimitExceededException;
import com.insurance.platform.security.CurrentUserProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatServiceTests {
    private static final String TRACE_ID = "01J4EXAMPLETRACE01";

    private AgentClient client;
    private ChatPersistenceService persistence;
    private ChatRateLimiter rateLimiter;
    private ChatIdempotencyCache idempotencyCache;
    private ChatHistoryService historyService;
    private CurrentUserProvider currentUserProvider;
    private ChatService service;
    private UUID conversationId;
    private UUID requestId;
    private UUID userMessageId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        client = mock(AgentClient.class);
        persistence = mock(ChatPersistenceService.class);
        rateLimiter = mock(ChatRateLimiter.class);
        idempotencyCache = mock(ChatIdempotencyCache.class);
        historyService = mock(ChatHistoryService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        service = new ChatService(
                client,
                persistence,
                rateLimiter,
                idempotencyCache,
                historyService,
                currentUserProvider,
                Clock.fixed(Instant.parse("2026-08-08T08:00:00Z"), ZoneOffset.UTC));
        conversationId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        userMessageId = UUID.randomUUID();
        userId = UUID.randomUUID();
        when(currentUserProvider.requireUserId()).thenReturn(userId);
        when(rateLimiter.acquire(userId)).thenReturn(new RateLimitDecision(true, 0));
        when(idempotencyCache.find(eq(userId), any())).thenReturn(Optional.empty());
        when(historyService.load(eq(userId), eq(conversationId), eq(2L), eq(1L)))
                .thenReturn(List.of());
        when(persistence.prepare(eq(conversationId), eq("question"), any(), any(), any()))
                .thenReturn(prepared(null));
    }

    @Test
    void successfulChatUsesPreparedIdsAndPersistsValidatedResponse() {
        UUID key = UUID.randomUUID();
        UUID assistantId = UUID.randomUUID();
        ChatResponse expected = new ChatResponse(
                conversationId, requestId, userMessageId, assistantId, "answer", List.of());
        when(client.chat(any(), eq(TRACE_ID)))
                .thenReturn(new AgentChatResponse(requestId, " answer ", List.of(), 1));
        when(persistence.completeSuccess(any(), eq("answer"), eq(List.of())))
                .thenReturn(expected);

        ChatResponse response = service.chat(
                new ChatRequest(conversationId, "question"), key, TRACE_ID);

        assertThat(response).isEqualTo(expected);
        verify(persistence, never()).completeFailure(any(), any(), any());
        verify(historyService, times(2)).evict(conversationId);
        ArgumentCaptor<IdempotencySummary> summaries =
                ArgumentCaptor.forClass(IdempotencySummary.class);
        verify(idempotencyCache, times(2)).put(eq(userId), eq(key), summaries.capture());
        assertThat(summaries.getAllValues()).extracting(IdempotencySummary::status)
                .containsExactly("PROCESSING", "SUCCEEDED");
    }

    @Test
    void successfulReplayNeverCallsAgent() {
        ChatResponse replay = new ChatResponse(
                conversationId, requestId, userMessageId, UUID.randomUUID(), "first", List.of());
        when(persistence.prepare(eq(conversationId), eq("question"), any(), any(), any()))
                .thenReturn(prepared(replay));

        assertThat(service.chat(
                new ChatRequest(conversationId, "question"), UUID.randomUUID(), TRACE_ID))
                .isEqualTo(replay);

        verify(client, never()).chat(any(), any());
        verify(historyService, never()).load(any(), any(), anyLong(), anyLong());
    }

    @Test
    void rejectedRateLimitStopsBeforeCacheDatabaseAndAgent() {
        when(rateLimiter.acquire(userId)).thenReturn(new RateLimitDecision(false, 17));

        assertThatThrownBy(() -> service.chat(
                        new ChatRequest(conversationId, "question"),
                        UUID.randomUUID(), TRACE_ID))
                .isInstanceOfSatisfying(RateLimitExceededException.class, exception ->
                        assertThat(exception.retryAfterSeconds()).isEqualTo(17));

        verify(idempotencyCache, never()).find(any(), any());
        verify(persistence, never()).prepare(any(), any(), any(), any(), any());
        verify(client, never()).chat(any(), any());
    }

    @Test
    void cachedIdempotencyRequestIdIsOnlyForwardedAsDatabaseHint() {
        UUID key = UUID.randomUUID();
        UUID cachedRequestId = UUID.randomUUID();
        when(idempotencyCache.find(userId, key)).thenReturn(Optional.of(
                new IdempotencySummary(
                        cachedRequestId,
                        requestHash(conversationId, "question"),
                        "SUCCEEDED",
                        Instant.parse("2026-08-08T07:00:00Z"))));

        when(client.chat(any(), any())).thenReturn(
                new AgentChatResponse(requestId, "answer", List.of(), 1));
        when(persistence.completeSuccess(any(), any(), any())).thenReturn(new ChatResponse(
                conversationId, requestId, userMessageId, UUID.randomUUID(), "answer", List.of()));

        service.chat(new ChatRequest(conversationId, "question"), key, TRACE_ID);

        verify(persistence).prepare(
                eq(conversationId), eq("question"), eq(key), any(), eq(cachedRequestId));
    }

    @Test
    void readTimeoutBecomesUnknownAndKeepsFrozenPublicError() {
        when(client.chat(any(), any()))
                .thenThrow(new AgentClientException(Kind.TIMEOUT, null));

        assertThatThrownBy(() -> service.chat(
                        new ChatRequest(conversationId, "question"),
                        UUID.randomUUID(), TRACE_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_SERVICE_TIMEOUT));

        verify(persistence).completeFailure(
                any(), eq(ChatRequestStatus.UNKNOWN), eq(ErrorCode.AI_SERVICE_TIMEOUT));
    }

    @Test
    void explicitProtocolFailureBecomesFailed() {
        when(client.chat(any(), any()))
                .thenThrow(new AgentClientException(Kind.PROTOCOL, null));

        assertThatThrownBy(() -> service.chat(
                        new ChatRequest(conversationId, "question"),
                        UUID.randomUUID(), TRACE_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_EXECUTION_FAILED));

        verify(persistence).completeFailure(
                any(), eq(ChatRequestStatus.FAILED), eq(ErrorCode.AI_EXECUTION_FAILED));
    }

    @Test
    void malformedResponseIsPersistedAsFailed() {
        when(client.chat(any(), any())).thenReturn(
                new AgentChatResponse(UUID.randomUUID(), "answer", List.of(), 1));

        assertThatThrownBy(() -> service.chat(
                        new ChatRequest(conversationId, "question"),
                        UUID.randomUUID(), TRACE_ID))
                .isInstanceOf(BusinessException.class);

        verify(persistence).completeFailure(
                any(), eq(ChatRequestStatus.FAILED), eq(ErrorCode.AI_EXECUTION_FAILED));
    }

    private PreparedChat prepared(ChatResponse replay) {
        return new PreparedChat(
                1L, 2L, conversationId, requestId, userMessageId,
                replay == null ? ChatRequestStatus.PROCESSING : ChatRequestStatus.SUCCEEDED,
                replay);
    }

    private static String requestHash(UUID conversationId, String message) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((conversationId + "\n" + message)
                            .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
