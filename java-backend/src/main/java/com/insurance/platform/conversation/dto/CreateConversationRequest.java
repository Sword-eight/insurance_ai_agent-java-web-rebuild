package com.insurance.platform.conversation.dto;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(@Size(max = 100) String title) {
    public CreateConversationRequest {
        title = title == null || title.isBlank() ? null : title.trim();
    }
}
