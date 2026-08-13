package com.insurance.platform.auth.vo;

import java.time.Instant;
import java.util.UUID;

/** Stable public user projection; database identifiers and password hashes never leave Java. */
public record UserView(UUID userId, String username, Instant createdAt) {}
