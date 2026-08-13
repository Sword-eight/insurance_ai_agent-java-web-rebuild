package com.insurance.platform.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultRedisKeyFactoryTests {

    @Test
    void createsVersionedKeysAndHashesOnlyTheIdempotencyKey() throws Exception {
        RedisKeyFactory keys = new DefaultRedisKeyFactory("test");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID conversationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID idempotencyKey = UUID.fromString("00000000-0000-0000-0000-000000000003");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(idempotencyKey.toString().getBytes(StandardCharsets.US_ASCII)));

        assertThat(keys.recentMessages(conversationId))
                .isEqualTo("iap:test:v1:chat:recent:" + conversationId);
        assertThat(keys.chatRate(userId, 1234))
                .isEqualTo("iap:test:v1:rate:chat:" + userId + ":1234");
        assertThat(keys.chatIdempotency(userId, idempotencyKey))
                .isEqualTo("iap:test:v1:idem:chat:" + userId + ":" + hash)
                .doesNotContain(idempotencyKey.toString());
    }
}
