package com.insurance.platform.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AgentChatRequest(
        @NotNull UUID requestId,
        @NotNull UUID sessionId,
        @NotBlank @Size(max = 4000) String message,
        @NotNull @Size(max = 10) List<@Valid HistoryMessage> history) {

    public AgentChatRequest {
        message = message == null ? null : message.trim();
        history = history == null ? null : List.copyOf(history);
    }

    @JsonIgnore
    @AssertTrue(message = "history must contain complete alternating user/assistant pairs")
    public boolean isHistoryPaired() {
        if (history == null || history.size() % 2 != 0) {
            return false;
        }
        for (int index = 0; index < history.size(); index++) {
            HistoryMessage item = history.get(index);
            String expectedRole = index % 2 == 0 ? "user" : "assistant";
            if (item == null || !expectedRole.equals(item.role())) {
                return false;
            }
        }
        return true;
    }

    @JsonIgnore
    @AssertTrue(message = "history content must not exceed 12000 characters")
    public boolean isHistoryWithinBudget() {
        if (history == null) {
            return false;
        }
        long total = history.stream()
                .filter(item -> item != null && item.content() != null)
                .mapToLong(item -> item.content().length())
                .sum();
        return total <= 12000;
    }
}
