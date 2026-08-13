package com.insurance.platform.chat.ratelimit;

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
}
