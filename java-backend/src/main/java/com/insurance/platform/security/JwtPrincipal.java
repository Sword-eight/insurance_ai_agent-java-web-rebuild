package com.insurance.platform.security;

import java.time.Instant;
import java.util.UUID;

/** Verified claims allowed to cross from the JWT adapter into Java security context. */
public record JwtPrincipal(UUID userId, Instant expiresAt) {}
