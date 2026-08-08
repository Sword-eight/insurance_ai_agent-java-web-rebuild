package com.insurance.platform.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.insurance.platform.chat.dto.ChatRequest;
import com.insurance.platform.chat.persistence.ChatPersistenceService;
import com.insurance.platform.chat.persistence.ChatRequestStatus;
import com.insurance.platform.chat.persistence.PreparedChat;
import com.insurance.platform.chat.vo.ChatResponse;
import com.insurance.platform.client.AgentClient;
import com.insurance.platform.client.dto.AgentChatResponse;
import com.insurance.platform.client.exception.AgentClientException;
import com.insurance.platform.client.exception.AgentClientException.Kind;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatServiceTests {
    private static final String TRACE_ID = "01J4EXAMPLETRACE01";

    private AgentClient client;
    private ChatPersistenceService persistence;
    private ChatService service;
    private UUID conversationId;
    private UUID requestId;
    private UUID userMessageId;

    @BeforeEach
    void setUp() {
        client = mock(AgentClient.class);
        persistence = mock(ChatPersistenceService.class);
        service = new ChatService(client, persistence);
        conversationId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        userMessageId = UUID.randomUUID();
        when(persistence.prepare(eq(conversationId), eq("question"), any(), any()))
                .thenReturn(prepared(null));
    }

    @Test
    void successfulChatUsesPreparedIdsAndPersistsValidatedResponse() {
        UUID assistantId = UUID.randomUUID();
        ChatResponse expected = new ChatResponse(
                conversationId, requestId, userMessageId, assistantId, "answer", List.of());
        when(client.chat(any(), eq(TRACE_ID)))
                .thenReturn(new AgentChatResponse(requestId, " answer ", List.of(), 1));
        when(persistence.completeSuccess(any(), eq("answer"), eq(List.of())))
                .thenReturn(expected);

        ChatResponse response = service.chat(
                new ChatRequest(conversationId, "question"), UUID.randomUUID(), TRACE_ID);

        assertThat(response).isEqualTo(expected);
        verify(persistence, never()).completeFailure(any(), any(), any());
    }

    @Test
    void successfulReplayNeverCallsAgent() {
        ChatResponse replay = new ChatResponse(
                conversationId, requestId, userMessageId, UUID.randomUUID(), "first", List.of());
        when(persistence.prepare(eq(conversationId), eq("question"), any(), any()))
                .thenReturn(prepared(replay));

        assertThat(service.chat(
                new ChatRequest(conversationId, "question"), UUID.randomUUID(), TRACE_ID))
                .isEqualTo(replay);

        verify(client, never()).chat(any(), any());
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
                1L, 2L, conversationId, requestId, userMessageId, List.of(), replay);
    }
}
