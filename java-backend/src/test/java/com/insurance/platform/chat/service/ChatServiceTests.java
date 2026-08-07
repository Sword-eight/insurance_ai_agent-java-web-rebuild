package com.insurance.platform.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.insurance.platform.chat.dto.ChatRequest;
import com.insurance.platform.chat.vo.ChatResponse;
import com.insurance.platform.client.AgentClient;
import com.insurance.platform.client.dto.AgentChatRequest;
import com.insurance.platform.client.dto.AgentChatResponse;
import com.insurance.platform.client.exception.AgentClientException;
import com.insurance.platform.client.exception.AgentClientException.Kind;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatServiceTests {

    private static final String TRACE_ID = "01J4EXAMPLETRACE01";

    @Test
    void successfulChatBuildsFrozenInternalRequestAndPublicResponse() {
        List<AgentChatRequest> calls = new ArrayList<>();
        AgentClient client = (request, traceId) -> {
            calls.add(request);
            assertThat(traceId).isEqualTo(TRACE_ID);
            return new AgentChatResponse(
                    request.requestId(),
                    " 真实回答 ",
                    List.of(Map.of(
                            "documentName", "条款.pdf",
                            "page", 3,
                            "snippet", "等待期条款",
                            "score", 0.87)),
                    12);
        };
        ChatService service = new ChatService(client);
        UUID conversationId = UUID.randomUUID();

        ChatResponse response = service.chat(
                new ChatRequest(conversationId, " 等待期多久？ "),
                UUID.randomUUID(),
                TRACE_ID);

        assertThat(calls).hasSize(1);
        AgentChatRequest internal = calls.get(0);
        assertThat(internal.sessionId()).isEqualTo(conversationId);
        assertThat(internal.message()).isEqualTo("等待期多久？");
        assertThat(internal.history()).isEmpty();
        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.requestId()).isEqualTo(internal.requestId());
        assertThat(response.answer()).isEqualTo("真实回答");
        assertThat(response.sources()).singleElement().satisfies(source -> {
            assertThat(source.documentName()).isEqualTo("条款.pdf");
            assertThat(source.page()).isEqualTo(3);
            assertThat(source.score()).isEqualTo(0.87);
        });
    }

    @Test
    void sameIdempotencyKeyProducesStableDistinctExecutionAndMessageIds() {
        List<UUID> requestIds = new ArrayList<>();
        AgentClient client = (request, traceId) -> {
            requestIds.add(request.requestId());
            return new AgentChatResponse(
                    request.requestId(), "answer", List.of(), 1);
        };
        ChatService service = new ChatService(client);
        UUID conversationId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        ChatRequest request = new ChatRequest(conversationId, "question");

        ChatResponse first = service.chat(request, key, TRACE_ID);
        ChatResponse second = service.chat(request, key, TRACE_ID);

        assertThat(second).isEqualTo(first);
        assertThat(requestIds).containsExactly(first.requestId(), first.requestId());
        assertThat(first.requestId()).isNotEqualTo(key);
        assertThat(first.userMessageId())
                .isNotEqualTo(first.assistantMessageId())
                .isNotEqualTo(first.requestId());
    }

    @Test
    void clientFailuresMapToFrozenPublicErrors() {
        assertMappedError(Kind.TIMEOUT, null, ErrorCode.AI_SERVICE_TIMEOUT);
        assertMappedError(Kind.UNAVAILABLE, null, ErrorCode.AI_SERVICE_UNAVAILABLE);
        assertMappedError(Kind.PROTOCOL, null, ErrorCode.AI_EXECUTION_FAILED);
        assertMappedError(
                Kind.REJECTED,
                "AI_REQUEST_IN_PROGRESS",
                ErrorCode.CHAT_REQUEST_IN_PROGRESS);
        assertMappedError(
                Kind.REJECTED,
                "AI_REQUEST_CONFLICT",
                ErrorCode.CHAT_IDEMPOTENCY_CONFLICT);
        assertMappedError(
                Kind.REJECTED,
                "AI_LLM_TIMEOUT",
                ErrorCode.AI_SERVICE_TIMEOUT);
        assertMappedError(
                Kind.REJECTED,
                "AI_INTERNAL_ERROR",
                ErrorCode.AI_EXECUTION_FAILED);
    }

    @Test
    void mismatchedResponseRequestIdFailsClosed() {
        AgentClient client = (request, traceId) -> new AgentChatResponse(
                UUID.randomUUID(), "answer", List.of(), 1);
        ChatService service = new ChatService(client);

        assertThatThrownBy(() -> service.chat(
                        new ChatRequest(UUID.randomUUID(), "question"),
                        UUID.randomUUID(),
                        TRACE_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.AI_EXECUTION_FAILED));
    }

    @Test
    void malformedSourceDoesNotLeakThroughPublicResponse() {
        AgentClient client = (request, traceId) -> new AgentChatResponse(
                request.requestId(),
                "answer",
                List.of(Map.of("documentName", "terms.pdf")),
                1);
        ChatService service = new ChatService(client);

        assertThatThrownBy(() -> service.chat(
                        new ChatRequest(UUID.randomUUID(), "question"),
                        UUID.randomUUID(),
                        TRACE_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.AI_EXECUTION_FAILED));
    }

    private static void assertMappedError(
            Kind kind,
            String internalCode,
            ErrorCode expected) {
        AgentClient client = (request, traceId) -> {
            throw new AgentClientException(kind, internalCode);
        };
        ChatService service = new ChatService(client);

        assertThatThrownBy(() -> service.chat(
                        new ChatRequest(UUID.randomUUID(), "question"),
                        UUID.randomUUID(),
                        TRACE_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(expected));
    }
}
