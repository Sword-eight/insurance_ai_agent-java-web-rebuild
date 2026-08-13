package com.insurance.platform.chat.cache;

import java.util.UUID;

public record CachedMessageSummary(
        UUID messageId,
        UUID requestId,
        String role,
        String content,
        long sequenceNo) {
}
