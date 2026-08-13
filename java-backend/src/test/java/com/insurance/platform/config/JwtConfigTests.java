package com.insurance.platform.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtConfigTests {

    private final JwtConfig config = new JwtConfig();

    @Test
    void missingMalformedAndShortSecretsFailClosed() {
        assertThatThrownBy(() -> config.jwtSigningKey(
                new JwtProperties("issuer", Duration.ofMinutes(30), "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT secret environment variable is required");
        assertThatThrownBy(() -> config.jwtSigningKey(
                new JwtProperties("issuer", Duration.ofMinutes(30), "not base64")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT secret must be valid Base64");
        assertThatThrownBy(() -> config.jwtSigningKey(
                new JwtProperties(
                        "issuer", Duration.ofMinutes(30),
                        java.util.Base64.getEncoder().encodeToString(new byte[16]))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT secret must decode to at least 32 bytes");
    }

    @Test
    void blankIssuerAndNonPositiveTtlFailClosed() {
        String validSecret = java.util.Base64.getEncoder().encodeToString(new byte[32]);
        assertThatThrownBy(() -> config.jwtSigningKey(
                new JwtProperties(" ", Duration.ofMinutes(30), validSecret)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT issuer must not be blank");
        assertThatThrownBy(() -> config.jwtSigningKey(
                new JwtProperties("issuer", Duration.ZERO, validSecret)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT access token TTL must be positive");
    }
}
