package com.insurance.platform.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurance.platform.chat.cache.CachedMessageSummary;
import com.insurance.platform.chat.cache.RedisRecentMessageCache;
import com.insurance.platform.chat.idempotency.IdempotencySummary;
import com.insurance.platform.chat.idempotency.RedisChatIdempotencyCache;
import com.insurance.platform.chat.ratelimit.RedisChatRateLimiter;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import com.insurance.platform.config.RedisProperties;
import com.insurance.platform.redis.DefaultRedisKeyFactory;
import com.insurance.platform.redis.RedisKeyFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisAdaptersIntegrationTests {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static RedisProperties properties;
    private static RedisKeyFactory keys;
    private static ObjectMapper objectMapper;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        properties = new RedisProperties();
        properties.setEnvironment("test");
        properties.setRecentMessagesTtl(Duration.ofMinutes(30));
        properties.setRateLimitTtl(Duration.ofSeconds(120));
        properties.setIdempotencyTtl(Duration.ofHours(24));
        properties.setChatRateLimitPerMinute(20);
        keys = new DefaultRedisKeyFactory("test");
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @AfterAll
    static void closeRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void recentMessagesUseRawArrayRecoverFromBadJsonAndHaveRealTtl() {
        RedisRecentMessageCache cache = new RedisRecentMessageCache(
                redis, objectMapper, keys, properties);
        UUID conversationId = UUID.randomUUID();
        List<CachedMessageSummary> messages = List.of(new CachedMessageSummary(
                UUID.randomUUID(), UUID.randomUUID(), "USER", "question", 1));

        cache.put(conversationId, messages);

        String key = keys.recentMessages(conversationId);
        assertThat(redis.opsForValue().get(key)).startsWith("[").doesNotContain("messages");
        assertThat(cache.find(conversationId)).contains(messages);
        assertThat(redis.getExpire(key)).isBetween(1L, 1800L);

        redis.opsForValue().set(key, "{bad-json");
        assertThat(cache.find(conversationId)).isEmpty();
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test
    void idempotencyPayloadHasFrozenShapeAndRealTtl() {
        RedisChatIdempotencyCache cache = new RedisChatIdempotencyCache(
                redis, objectMapper, keys, properties);
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        IdempotencySummary summary = new IdempotencySummary(
                UUID.randomUUID(), "a".repeat(64), "PROCESSING",
                Instant.parse("2026-08-08T08:00:00Z"));

        cache.put(userId, keyId, summary);

        String key = keys.chatIdempotency(userId, keyId);
        assertThat(redis.opsForValue().get(key))
                .contains("requestId", "requestHash", "status", "updatedAt")
                .doesNotContain("schemaVersion");
        assertThat(cache.find(userId, keyId)).contains(summary);
        assertThat(redis.getExpire(key)).isBetween(1L, 86400L);
    }

    @Test
    void rateLimitAllowsTwentyRejectsTwentyFirstAndSetsTtlAtomically() {
        RedisChatRateLimiter limiter = new RedisChatRateLimiter(
                redis,
                keys,
                properties,
                Clock.fixed(Instant.parse("2026-08-08T08:00:43Z"), ZoneOffset.UTC));
        UUID userId = UUID.randomUUID();

        for (int index = 0; index < 20; index++) {
            assertThat(limiter.acquire(userId).allowed()).isTrue();
        }
        assertThat(limiter.acquire(userId).allowed()).isFalse();
        assertThat(limiter.acquire(userId).retryAfterSeconds()).isEqualTo(17);
        long minuteEpoch = Instant.parse("2026-08-08T08:00:43Z").getEpochSecond() / 60;
        assertThat(redis.getExpire(keys.chatRate(userId, minuteEpoch)))
                .isBetween(1L, 120L);

        RedisChatRateLimiter nextWindow = new RedisChatRateLimiter(
                redis,
                keys,
                properties,
                Clock.fixed(Instant.parse("2026-08-08T08:01:00Z"), ZoneOffset.UTC));
        assertThat(nextWindow.acquire(userId).allowed()).isTrue();
    }

    @Test
    void optionalCachesFailOpenIncludingPostCommitEviction() {
        StringRedisTemplate unavailable = mock(StringRedisTemplate.class);
        when(unavailable.opsForValue()).thenThrow(
                new RedisConnectionFailureException("test outage"));
        when(unavailable.delete(any(String.class))).thenThrow(
                new RedisConnectionFailureException("test outage"));
        RedisRecentMessageCache recent = new RedisRecentMessageCache(
                unavailable, objectMapper, keys, properties);
        RedisChatIdempotencyCache idempotency = new RedisChatIdempotencyCache(
                unavailable, objectMapper, keys, properties);
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        assertThat(recent.find(UUID.randomUUID())).isEmpty();
        assertThat(idempotency.find(userId, keyId)).isEmpty();
        assertThatCode(() -> recent.evict(UUID.randomUUID())).doesNotThrowAnyException();
        assertThatCode(() -> idempotency.put(
                userId,
                keyId,
                new IdempotencySummary(
                        UUID.randomUUID(), "b".repeat(64), "SUCCEEDED", Instant.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void rateLimitFailsClosedWhenRedisIsUnavailable() {
        StringRedisTemplate unavailable = mock(StringRedisTemplate.class);
        when(unavailable.execute(any(), any(List.class), any())).thenThrow(
                new RedisConnectionFailureException("test outage"));
        RedisChatRateLimiter limiter = new RedisChatRateLimiter(
                unavailable,
                keys,
                properties,
                Clock.fixed(Instant.parse("2026-08-08T08:00:43Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> limiter.acquire(UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.RATE_LIMIT_SERVICE_UNAVAILABLE));
    }
}
