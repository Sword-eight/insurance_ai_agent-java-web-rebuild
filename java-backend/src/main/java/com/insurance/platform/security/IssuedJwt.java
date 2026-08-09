package com.insurance.platform.security;

import java.time.Instant;

/** Signed access token plus its public expiry metadata. */
public record IssuedJwt(String value, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedJwt[value=<redacted>, expiresAt=" + expiresAt + "]";
    }
}
