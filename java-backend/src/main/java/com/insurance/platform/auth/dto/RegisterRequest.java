package com.insurance.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public registration contract. Password byte-length validation remains a Service concern. */
public record RegisterRequest(
        @NotBlank
        @Size(max = 128)
        String username,
        @NotBlank
        @Size(min = 8, max = 72)
        String password) {

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", password=<redacted>]";
    }
}
