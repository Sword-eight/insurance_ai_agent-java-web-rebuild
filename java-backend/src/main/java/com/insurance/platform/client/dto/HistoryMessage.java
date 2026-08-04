package com.insurance.platform.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record HistoryMessage(
        @NotBlank @Pattern(regexp = "user|assistant") String role,
        @NotBlank @Size(max = 4000) String content) {

    public HistoryMessage {
        role = role == null ? null : role.trim();
        content = content == null ? null : content.trim();
    }
}
