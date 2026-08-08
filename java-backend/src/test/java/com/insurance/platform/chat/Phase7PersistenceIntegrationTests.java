package com.insurance.platform.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.insurance.platform.chat.dto.ChatRequest;
import com.insurance.platform.chat.service.ChatService;
import com.insurance.platform.chat.vo.ChatResponse;
import com.insurance.platform.client.AgentClient;
import com.insurance.platform.client.dto.AgentChatRequest;
import com.insurance.platform.client.dto.AgentChatResponse;
import com.insurance.platform.client.exception.AgentClientException;
import com.insurance.platform.client.exception.AgentClientException.Kind;
import com.insurance.platform.common.api.PageResponse;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import com.insurance.platform.conversation.dto.CreateConversationRequest;
import com.insurance.platform.conversation.service.ConversationService;
import com.insurance.platform.conversation.vo.ConversationView;
import com.insurance.platform.message.service.MessageService;
import com.insurance.platform.message.vo.MessageView;
import com.insurance.platform.security.CurrentUserProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@AutoConfigureMockMvc
class Phase7PersistenceIntegrationTests {
    private static final String TRACE_ID = "01J4EXAMPLETRACE01";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ConversationService conversationService;
    @Autowired private ChatService chatService;
    @Autowired private MessageService messageService;
    @Autowired private MockMvc mockMvc;
    @MockBean private CurrentUserProvider currentUserProvider;
    @MockBean private AgentClient agentClient;

    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM iap_chat_message");
        jdbc.update("DELETE FROM iap_chat_request");
        jdbc.update("DELETE FROM iap_conversation");
        jdbc.update("DELETE FROM iap_user");
        reset(agentClient, currentUserProvider);
        userId = insertUser("phase7-user");
        when(currentUserProvider.requireUserId()).thenReturn(userId);
    }

    @Test
    void migrationConversationOwnershipAndPaginationWorkTogether() {
        ConversationView first = conversationService.create(
                new CreateConversationRequest(" first "));
        ConversationView second = conversationService.create(
                new CreateConversationRequest("second"));

        PageResponse<ConversationView> page = conversationService.list(1, 1);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).containsExactly(second);
        assertThat(conversationService.get(first.conversationId())).isEqualTo(first);

        UUID foreignUser = insertUser("foreign-user");
        when(currentUserProvider.requireUserId()).thenReturn(foreignUser);
        ConversationView foreign = conversationService.create(
                new CreateConversationRequest("foreign"));
        when(currentUserProvider.requireUserId()).thenReturn(userId);

        assertThatThrownBy(() -> conversationService.get(foreign.conversationId()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.CONVERSATION_ACCESS_DENIED));
        assertThatThrownBy(() -> conversationService.get(UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    @Test
    void successPersistsSourcesReplaysAndSuppliesOnlyCompletePriorPairs() {
        UUID conversationId = conversationService.create(
                new CreateConversationRequest("chat")).conversationId();
        List<AgentChatRequest> calls = new ArrayList<>();
        AtomicBoolean transactionSeen = new AtomicBoolean(true);
        when(agentClient.chat(any(), anyString())).thenAnswer(invocation -> {
            AgentChatRequest request = invocation.getArgument(0);
            calls.add(request);
            transactionSeen.set(TransactionSynchronizationManager.isActualTransactionActive());
            return new AgentChatResponse(
                    request.requestId(),
                    "answer-" + calls.size(),
                    List.of(Map.of(
                            "documentName", "terms.pdf",
                            "page", 3,
                            "snippet", "waiting period",
                            "score", 0.87)),
                    5);
        });

        UUID firstKey = UUID.randomUUID();
        ChatResponse first = chatService.chat(
                new ChatRequest(conversationId, "question-1"), firstKey, TRACE_ID);
        ChatResponse replay = chatService.chat(
                new ChatRequest(conversationId, "question-1"), firstKey, TRACE_ID);
        ChatResponse second = chatService.chat(
                new ChatRequest(conversationId, "question-2"), UUID.randomUUID(), TRACE_ID);

        assertThat(replay).isEqualTo(first);
        assertThat(first.sources()).singleElement().satisfies(source ->
                assertThat(source.documentName()).isEqualTo("terms.pdf"));
        assertThat(second.answer()).isEqualTo("answer-2");
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).history()).isEmpty();
        assertThat(calls.get(1).history()).extracting("role")
                .containsExactly("user", "assistant");
        assertThat(calls.get(1).history()).extracting("content")
                .containsExactly("question-1", "answer-1");
        assertThat(transactionSeen).isFalse();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iap_chat_request", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iap_chat_message", Long.class)).isEqualTo(4);

        PageResponse<MessageView> messages = messageService.list(conversationId, 1, 20);
        assertThat(messages.total()).isEqualTo(4);
        assertThat(messages.items()).extracting(MessageView::role)
                .containsExactly("USER", "ASSISTANT", "USER", "ASSISTANT");
    }

    @Test
    void sameKeyWithDifferentPayloadConflictsWithoutSecondAgentCall() {
        UUID conversationId = conversationService.create(
                new CreateConversationRequest("chat")).conversationId();
        when(agentClient.chat(any(), anyString())).thenAnswer(invocation -> {
            AgentChatRequest request = invocation.getArgument(0);
            return new AgentChatResponse(request.requestId(), "answer", List.of(), 1);
        });
        UUID key = UUID.randomUUID();
        chatService.chat(new ChatRequest(conversationId, "first"), key, TRACE_ID);

        assertThatThrownBy(() -> chatService.chat(
                        new ChatRequest(conversationId, "different"), key, TRACE_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.CHAT_IDEMPOTENCY_CONFLICT));
        verify(agentClient, times(1)).chat(any(), anyString());
        verifyNoMoreInteractions(agentClient);
    }

    @Test
    void readTimeoutPersistsUnknownAndReplayNeverCallsAgent() {
        UUID conversationId = conversationService.create(
                new CreateConversationRequest("chat")).conversationId();
        when(agentClient.chat(any(), anyString()))
                .thenThrow(new AgentClientException(Kind.TIMEOUT, null));
        UUID key = UUID.randomUUID();

        assertTimeout(conversationId, key);
        assertTimeout(conversationId, key);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM iap_chat_request", String.class)).isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iap_chat_message", Long.class)).isEqualTo(1);
        verify(agentClient, times(1)).chat(any(), anyString());
        verifyNoMoreInteractions(agentClient);
    }

    @Test
    void explicitDependencyFailurePersistsFailedWithoutAssistant() {
        UUID conversationId = conversationService.create(
                new CreateConversationRequest("chat")).conversationId();
        when(agentClient.chat(any(), anyString()))
                .thenThrow(new AgentClientException(Kind.UNAVAILABLE, null));

        assertThatThrownBy(() -> chatService.chat(
                        new ChatRequest(conversationId, "question"),
                        UUID.randomUUID(), TRACE_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM iap_chat_request", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iap_chat_message WHERE role='ASSISTANT'", Long.class))
                .isZero();
    }

    @Test
    void duplicateWhileFirstRequestIsRunningReturnsInProgressWithoutSecondCall()
            throws Exception {
        UUID conversationId = conversationService.create(
                new CreateConversationRequest("chat")).conversationId();
        UUID key = UUID.randomUUID();
        CountDownLatch enteredAgent = new CountDownLatch(1);
        CountDownLatch releaseAgent = new CountDownLatch(1);
        when(agentClient.chat(any(), anyString())).thenAnswer(invocation -> {
            AgentChatRequest request = invocation.getArgument(0);
            enteredAgent.countDown();
            if (!releaseAgent.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test did not release agent");
            }
            return new AgentChatResponse(request.requestId(), "answer", List.of(), 1);
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ChatResponse> first = executor.submit(() -> chatService.chat(
                    new ChatRequest(conversationId, "question"), key, TRACE_ID));
            assertThat(enteredAgent.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> chatService.chat(
                            new ChatRequest(conversationId, "question"), key, TRACE_ID))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.errorCode())
                                    .isEqualTo(ErrorCode.CHAT_REQUEST_IN_PROGRESS));

            releaseAgent.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).answer()).isEqualTo("answer");
            verify(agentClient, times(1)).chat(any(), anyString());
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM iap_chat_request", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM iap_chat_message", Long.class)).isEqualTo(2);
        } finally {
            releaseAgent.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void historyIsLimitedToFiveCompletePairs() {
        UUID conversationId = conversationService.create(
                new CreateConversationRequest("chat")).conversationId();
        List<AgentChatRequest> calls = new ArrayList<>();
        when(agentClient.chat(any(), anyString())).thenAnswer(invocation -> {
            AgentChatRequest request = invocation.getArgument(0);
            calls.add(request);
            return new AgentChatResponse(
                    request.requestId(), "answer-" + calls.size(), List.of(), 1);
        });

        for (int index = 1; index <= 7; index++) {
            chatService.chat(
                    new ChatRequest(conversationId, "question-" + index),
                    UUID.randomUUID(), TRACE_ID);
        }

        assertThat(calls.get(6).history()).hasSize(10);
        assertThat(calls.get(6).history()).extracting("content")
                .containsExactly(
                        "question-2", "answer-2",
                        "question-3", "answer-3",
                        "question-4", "answer-4",
                        "question-5", "answer-5",
                        "question-6", "answer-6");
    }

    @Test
    void publicConversationChatAndMessageEndpointsUseFrozenEnvelope()
            throws Exception {
        when(agentClient.chat(any(), anyString())).thenAnswer(invocation -> {
            AgentChatRequest request = invocation.getArgument(0);
            return new AgentChatResponse(request.requestId(), "answer", List.of(), 1);
        });
        MvcResult created = mockMvc.perform(post("/api/v1/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"claims\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        String conversationId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsByteArray())
                .path("data").path("conversationId").asText();

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"%s","message":"question"}
                                """.formatted(conversationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("answer"));
        mockMvc.perform(get("/api/v1/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/v1/conversations/{id}", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("claims"));
        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].role").value("USER"))
                .andExpect(jsonPath("$.data.items[1].role").value("ASSISTANT"));
    }

    private void assertTimeout(UUID conversationId, UUID key) {
        assertThatThrownBy(() -> chatService.chat(
                        new ChatRequest(conversationId, "question"), key, TRACE_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_SERVICE_TIMEOUT));
    }

    private UUID insertUser(String username) {
        UUID publicId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO iap_user
                    (user_id, username, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, publicId.toString(), username, "test-only-not-a-real-password-hash");
        return publicId;
    }
}
