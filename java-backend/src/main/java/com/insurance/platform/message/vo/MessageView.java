package com.insurance.platform.message.vo;

import java.time.Instant;
import java.util.UUID;

public record MessageView(
        UUID messageId,
        UUID requestId,
        String role,
        String content,
        Instant createdAt) {
}
