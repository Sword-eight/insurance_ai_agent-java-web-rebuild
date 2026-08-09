package com.insurance.platform.chat.ratelimit;

import java.util.UUID;

public interface ChatRateLimiter {

    RateLimitDecision acquire(UUID userId);
}
