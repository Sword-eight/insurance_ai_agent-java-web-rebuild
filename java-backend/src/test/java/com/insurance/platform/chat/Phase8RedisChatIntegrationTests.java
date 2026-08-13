package com.insurance.platform.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.insurance.platform.chat.dto.ChatRequest;
import com.insurance.platform.chat.service.ChatService;
import com.insurance.platform.chat.vo.ChatResponse;
import com.insurance.platform.client.AgentClient;
import com.insurance.platform.client.dto.AgentChatRequest;
import com.insurance.platform.client.dto.AgentChatResponse;
import com.insurance.platform.conversation.dto.CreateConversationRequest;
import com.insurance.platform.conversation.service.ConversationService;
import com.insurance.platform.redis.RedisKeyFactory;
import com.insurance.platform.security.CurrentUserProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase8RedisChatIntegrationTests {

    private static final String TRACE_ID = "01J4EXAMPLETRACE01";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("insurance.redis.environment", () -> "phase8-it");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ConversationService conversationService;
    @Autowired private ChatService chatService;
    @Autowired private StringRedisTemplate redis;
    @Autowired private RedisKeyFactory keys;
    @MockBean private CurrentUserProvider currentUserProvider;
    @MockBean private AgentClient agentClient;

    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM iap_chat_message");
        jdbc.update("DELETE FROM iap_chat_request");
        jdbc.update("DELETE FROM iap_conversation");
        jdbc.update("DELETE FROM iap_user");
        reset(currentUserProvider, agentClient);
        userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO iap_user
                    (user_id, username, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId.toString(), "phase8-real-redis", "test-only-hash");
        when(currentUserProvider.requireUserId()).thenReturn(userId);
    }

    @Test
    void realRedisAndDatabaseCompleteChatReplayAndHistoryFlow() {
        UUID conversationId = conversationService.create(
                new CreateConversationRequest("phase8")).conversationId();
        List<AgentChatRequest> calls = new ArrayList<>();
        when(agentClient.chat(any(), anyString())).thenAnswer(invocation -> {
            AgentChatRequest request = invocation.getArgument(0);
            calls.add(request);
            return new AgentChatResponse(
                    request.requestId(), "answer-" + calls.size(), List.of(), 1);
        });

        UUID firstKey = UUID.randomUUID();
        ChatResponse first = chatService.chat(
                new ChatRequest(conversationId, "question-1"), firstKey, TRACE_ID);
        ChatResponse replay = chatService.chat(
                new ChatRequest(conversationId, "question-1"), firstKey, TRACE_ID);
        UUID secondKey = UUID.randomUUID();
        ChatResponse second = chatService.chat(
                new ChatRequest(conversationId, "question-2"), secondKey, TRACE_ID);

        assertThat(replay).isEqualTo(first);
        assertThat(second.answer()).isEqualTo("answer-2");
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).history()).isEmpty();
        assertThat(calls.get(1).history()).extracting("role", "content")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("user", "question-1"),
                        org.assertj.core.groups.Tuple.tuple("assistant", "answer-1"));
        verify(agentClient, times(2)).chat(any(), anyString());

        String firstIdempotencyKey = keys.chatIdempotency(userId, firstKey);
        assertThat(redis.opsForValue().get(firstIdempotencyKey))
                .contains(first.requestId().toString(), "\"status\":\"SUCCEEDED\"");
        assertThat(redis.getExpire(firstIdempotencyKey)).isBetween(1L, 86400L);
        assertThat(redis.hasKey(keys.recentMessages(conversationId))).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iap_chat_request", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM iap_chat_message", Long.class)).isEqualTo(4);
    }
}
