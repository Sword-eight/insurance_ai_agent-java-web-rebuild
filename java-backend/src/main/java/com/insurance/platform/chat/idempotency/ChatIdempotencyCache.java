package com.insurance.platform.chat.idempotency;

import java.util.Optional;
import java.util.UUID;

public interface ChatIdempotencyCache {

    Optional<IdempotencySummary> find(UUID userId, UUID idempotencyKey);

    void put(UUID userId, UUID idempotencyKey, IdempotencySummary summary);
}
