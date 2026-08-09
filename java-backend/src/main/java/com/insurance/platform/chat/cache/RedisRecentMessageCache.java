package com.insurance.platform.chat.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.platform.config.RedisProperties;
import com.insurance.platform.redis.RedisKeyFactory;
import com.insurance.platform.common.trace.TraceIdContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisRecentMessageCache implements RecentMessageCache {

    private static final Logger logger = LoggerFactory.getLogger(RedisRecentMessageCache.class);
    private static final int MESSAGE_LIMIT = 10;
    private static final int CONTENT_LIMIT = 4000;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RedisKeyFactory keys;
    private final RedisProperties properties;

    public RedisRecentMessageCache(
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
    public Optional<List<CachedMessageSummary>> find(UUID conversationId) {
        String key = keys.recentMessages(conversationId);
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            List<CachedMessageSummary> messages = objectMapper.readValue(
                    json, new TypeReference<>() {});
            validate(messages);
            return Optional.of(List.copyOf(messages));
        } catch (JsonProcessingException | RuntimeException exception) {
            logger.warn(
                    "Ignoring Redis value, keyType=chat_recent, traceId={}, conversationId={}, reason={}",
                    TraceIdContext.currentTraceId(),
                    conversationId,
                    exception.getClass().getSimpleName());
            evict(key, conversationId);
            return Optional.empty();
        }
    }

    @Override
    public void put(UUID conversationId, List<CachedMessageSummary> messages) {
        try {
            validate(messages);
            redis.opsForValue().set(
                    keys.recentMessages(conversationId),
                    objectMapper.writeValueAsString(messages),
                    properties.getRecentMessagesTtl());
        } catch (JsonProcessingException | RuntimeException exception) {
            logger.warn(
                    "Unable to write Redis value, keyType=chat_recent, traceId={}, conversationId={}, reason={}",
                    TraceIdContext.currentTraceId(),
                    conversationId,
                    exception.getClass().getSimpleName());
        }
    }

    @Override
    public void evict(UUID conversationId) {
        evict(keys.recentMessages(conversationId), conversationId);
    }

    private void evict(String key, UUID conversationId) {
        try {
            redis.delete(key);
        } catch (RuntimeException exception) {
            logger.warn(
                    "Unable to evict Redis key, keyType=chat_recent, traceId={}, conversationId={}, reason={}",
                    TraceIdContext.currentTraceId(),
                    conversationId,
                    exception.getClass().getSimpleName());
        }
    }

    private static void validate(List<CachedMessageSummary> messages) {
        if (messages == null || messages.size() > MESSAGE_LIMIT) {
            throw new IllegalArgumentException("recent-message cache size is invalid");
        }
        long previousSequence = 0;
        for (CachedMessageSummary message : messages) {
            if (message == null
                    || message.messageId() == null
                    || message.requestId() == null
                    || !("USER".equals(message.role()) || "ASSISTANT".equals(message.role()))
                    || message.content() == null
                    || message.content().isBlank()
                    || message.content().length() > CONTENT_LIMIT
                    || message.sequenceNo() <= previousSequence) {
                throw new IllegalArgumentException("recent-message cache payload is invalid");
            }
            previousSequence = message.sequenceNo();
        }
    }
}
