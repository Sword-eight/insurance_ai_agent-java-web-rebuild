package com.insurance.platform.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Environment-backed JWT configuration. Secret values must never be logged. */
@ConfigurationProperties("insurance.security.jwt")
public record JwtProperties(String issuer, Duration accessTokenTtl, String secretBase64) {}
