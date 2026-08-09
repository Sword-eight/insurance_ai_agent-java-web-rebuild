package com.insurance.platform.redis;

import java.util.UUID;

public interface RedisKeyFactory {

    String recentMessages(UUID conversationId);

    String chatRate(UUID userId, long minuteEpoch);

    String chatIdempotency(UUID userId, UUID idempotencyKey);
}
