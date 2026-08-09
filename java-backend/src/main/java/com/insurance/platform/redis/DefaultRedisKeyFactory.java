package com.insurance.platform.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class DefaultRedisKeyFactory implements RedisKeyFactory {

    private static final Pattern ENVIRONMENT = Pattern.compile("[a-z0-9-]+");

    private final String prefix;

    public DefaultRedisKeyFactory(String environment) {
        if (environment == null || !ENVIRONMENT.matcher(environment).matches()) {
            throw new IllegalArgumentException("Redis environment must use lowercase ASCII");
        }
        this.prefix = "iap:" + environment + ":v1:";
    }

    @Override
    public String recentMessages(UUID conversationId) {
        return prefix + "chat:recent:" + required(conversationId);
    }

    @Override
    public String chatRate(UUID userId, long minuteEpoch) {
        if (minuteEpoch < 0) {
            throw new IllegalArgumentException("minuteEpoch must not be negative");
        }
        return prefix + "rate:chat:" + required(userId) + ":" + minuteEpoch;
    }

    @Override
    public String chatIdempotency(UUID userId, UUID idempotencyKey) {
        return prefix + "idem:chat:" + required(userId) + ":" + sha256(required(idempotencyKey));
    }

    private static UUID required(UUID value) {
        return Objects.requireNonNull(value, "Redis key dimension must not be null");
    }

    private static String sha256(UUID value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
