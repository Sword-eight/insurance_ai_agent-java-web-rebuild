package com.insurance.platform.conversation.vo;

import java.time.Instant;
import java.util.UUID;

public record ConversationView(
        UUID conversationId,
        String title,
        String status,
        Instant createdAt) {
}
