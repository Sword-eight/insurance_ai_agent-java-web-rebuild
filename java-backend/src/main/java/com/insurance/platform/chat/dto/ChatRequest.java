package com.insurance.platform.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** POST /api/v1/chat/messages 的公共请求 DTO。 */
public record ChatRequest(
        @NotNull UUID conversationId,
        @NotBlank @Size(max = 4000) String message) {

    public ChatRequest {
        message = message == null ? null : message.trim();
    }
}
