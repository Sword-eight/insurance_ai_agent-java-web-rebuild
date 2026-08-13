package com.insurance.platform.chat.idempotency;

import java.time.Instant;
import java.util.UUID;

public record IdempotencySummary(
        UUID requestId,
        String requestHash,
        String status,
        Instant updatedAt) {
}
