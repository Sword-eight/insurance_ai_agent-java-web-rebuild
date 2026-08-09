package com.insurance.platform.chat.ratelimit;

import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import com.insurance.platform.config.RedisProperties;
import com.insurance.platform.redis.RedisKeyFactory;
import com.insurance.platform.common.trace.TraceIdContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisChatRateLimiter implements ChatRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(RedisChatRateLimiter.class);
    private static final String SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('TTL', KEYS[1])
            return {count, ttl}
            """;

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> RATE_SCRIPT =
            new DefaultRedisScript<>(SCRIPT, List.class);

    private final StringRedisTemplate redis;
    private final RedisKeyFactory keys;
    private final RedisProperties properties;
    private final Clock clock;

    public RedisChatRateLimiter(
            StringRedisTemplate redis,
            RedisKeyFactory keys,
            RedisProperties properties,
            Clock clock) {
        this.redis = redis;
        this.keys = keys;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitDecision acquire(UUID userId) {
        Instant now = clock.instant();
        long epochSecond = now.getEpochSecond();
        long minuteEpoch = Math.floorDiv(epochSecond, 60);
        String key = keys.chatRate(userId, minuteEpoch);
        try {
            List<Long> result = redis.execute(
                    RATE_SCRIPT,
                    List.of(key),
                    Long.toString(properties.getRateLimitTtl().toSeconds()));
            if (result == null || result.size() != 2
                    || result.get(0) == null || result.get(1) == null
                    || result.get(1) < 1) {
                throw new IllegalStateException("Redis rate script returned an invalid result");
            }
            if (result.get(0) <= properties.getChatRateLimitPerMinute()) {
                return new RateLimitDecision(true, 0);
            }
            long retryAfter = Math.max(1, 60 - Math.floorMod(epochSecond, 60));
            return new RateLimitDecision(false, retryAfter);
        } catch (RuntimeException exception) {
            logger.warn(
                    "Redis limiter unavailable, keyType=chat_rate, traceId={}, userId={}, reason={}",
                    TraceIdContext.currentTraceId(),
                    userId,
                    exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.RATE_LIMIT_SERVICE_UNAVAILABLE);
        }
    }
}
