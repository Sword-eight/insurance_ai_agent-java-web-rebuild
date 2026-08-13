package com.insurance.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public login contract. */
public record LoginRequest(
        @NotBlank
        @Size(max = 128)
        String username,
        @NotBlank
        @Size(min = 8, max = 72)
        String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=<redacted>]";
    }
}
