package com.insurance.platform.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "insurance.redis")
public class RedisProperties {

    @NotBlank
    @Pattern(regexp = "[a-z0-9-]+")
    private String environment = "dev";

    @NotNull
    private Duration recentMessagesTtl = Duration.ofMinutes(30);

    @NotNull
    private Duration rateLimitTtl = Duration.ofSeconds(120);

    @NotNull
    private Duration idempotencyTtl = Duration.ofHours(24);

    @Min(1)
    private int chatRateLimitPerMinute = 20;

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Duration getRecentMessagesTtl() {
        return recentMessagesTtl;
    }

    public void setRecentMessagesTtl(Duration recentMessagesTtl) {
        this.recentMessagesTtl = recentMessagesTtl;
    }

    public Duration getRateLimitTtl() {
        return rateLimitTtl;
    }

    public void setRateLimitTtl(Duration rateLimitTtl) {
        this.rateLimitTtl = rateLimitTtl;
    }

    public Duration getIdempotencyTtl() {
        return idempotencyTtl;
    }

    public void setIdempotencyTtl(Duration idempotencyTtl) {
        this.idempotencyTtl = idempotencyTtl;
    }

    public int getChatRateLimitPerMinute() {
        return chatRateLimitPerMinute;
    }

    public void setChatRateLimitPerMinute(int chatRateLimitPerMinute) {
        this.chatRateLimitPerMinute = chatRateLimitPerMinute;
    }

    @AssertTrue(message = "recentMessagesTtl must be positive")
    public boolean isRecentMessagesTtlPositive() {
        return recentMessagesTtl != null && !recentMessagesTtl.isZero()
                && !recentMessagesTtl.isNegative();
    }

    @AssertTrue(message = "rateLimitTtl must be positive")
    public boolean isRateLimitTtlPositive() {
        return rateLimitTtl != null && !rateLimitTtl.isZero()
                && !rateLimitTtl.isNegative();
    }

    @AssertTrue(message = "idempotencyTtl must be positive")
    public boolean isIdempotencyTtlPositive() {
        return idempotencyTtl != null && !idempotencyTtl.isZero()
                && !idempotencyTtl.isNegative();
    }
}
