package com.insurance.platform.chat.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.platform.config.RedisProperties;
import com.insurance.platform.redis.RedisKeyFactory;
import com.insurance.platform.common.trace.TraceIdContext;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisChatIdempotencyCache implements ChatIdempotencyCache {

    private static final Logger logger = LoggerFactory.getLogger(RedisChatIdempotencyCache.class);
    private static final Pattern REQUEST_HASH = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> STATUSES = Set.of(
            "RECEIVED", "PROCESSING", "SUCCEEDED", "FAILED", "UNKNOWN");

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RedisKeyFactory keys;
    private final RedisProperties properties;

    public RedisChatIdempotencyCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            RedisKeyFactory keys,
            RedisProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.keys = keys;
        this.properties = properties;
    }

    @Override
    public Optional<IdempotencySummary> find(UUID userId, UUID idempotencyKey) {
        String key = keys.chatIdempotency(userId, idempotencyKey);
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            IdempotencySummary summary = objectMapper.readValue(json, IdempotencySummary.class);
            validate(summary);
            return Optional.of(summary);
        } catch (JsonProcessingException | RuntimeException exception) {
            logger.warn(
                    "Ignoring Redis value, keyType=chat_idempotency, traceId={}, reason={}",
                    TraceIdContext.currentTraceId(),
                    exception.getClass().getSimpleName());
            evict(key);
            return Optional.empty();
        }
    }

    @Override
    public void put(UUID userId, UUID idempotencyKey, IdempotencySummary summary) {
        try {
            validate(summary);
            redis.opsForValue().set(
                    keys.chatIdempotency(userId, idempotencyKey),
                    objectMapper.writeValueAsString(summary),
                    properties.getIdempotencyTtl());
        } catch (JsonProcessingException | RuntimeException exception) {
            logger.warn(
                    "Unable to write Redis value, keyType=chat_idempotency, traceId={}, requestId={}, reason={}",
                    TraceIdContext.currentTraceId(),
                    summary == null ? null : summary.requestId(),
                    exception.getClass().getSimpleName());
        }
    }

    private void evict(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException exception) {
            logger.warn(
                    "Unable to evict Redis key, keyType=chat_idempotency, traceId={}, reason={}",
                    TraceIdContext.currentTraceId(),
                    exception.getClass().getSimpleName());
        }
    }

    private static void validate(IdempotencySummary summary) {
        if (summary == null
                || summary.requestId() == null
                || summary.requestHash() == null
                || !REQUEST_HASH.matcher(summary.requestHash()).matches()
                || !STATUSES.contains(summary.status())
                || summary.updatedAt() == null) {
            throw new IllegalArgumentException("chat-idempotency cache payload is invalid");
        }
    }
}
