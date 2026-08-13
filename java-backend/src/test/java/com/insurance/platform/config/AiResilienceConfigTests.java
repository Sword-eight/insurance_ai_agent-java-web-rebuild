package com.insurance.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurance.platform.client.exception.AgentClientException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiResilienceConfigTests {
    @Test
    void buildsSeparateBreakersWithFrozenThresholdsAndFailureClassification() {
        AiResilienceProperties properties = new AiResilienceProperties(
                20, 10, 50, Duration.ofSeconds(30), 3);
        AiResilienceConfig config = new AiResilienceConfig();
        CircuitBreaker agent = config.agentCircuitBreaker(properties);
        CircuitBreaker knowledge = config.knowledgeCircuitBreaker(properties);

        assertThat(agent).isNotSameAs(knowledge);
        assertThat(agent.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(20);
        assertThat(agent.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(10);
        assertThat(agent.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50);
        assertThat(agent.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState()
                .apply(1)).isEqualTo(30_000L);
        assertThat(agent.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState())
                .isEqualTo(3);

        agent.onError(1, java.util.concurrent.TimeUnit.MILLISECONDS,
                new AgentClientException(AgentClientException.Kind.REJECTED, "AI_VALIDATION_ERROR"));
        assertThat(agent.getMetrics().getNumberOfFailedCalls()).isZero();
        agent.onError(1, java.util.concurrent.TimeUnit.MILLISECONDS,
                new AgentClientException(AgentClientException.Kind.UPSTREAM_FAILURE, "AI_INTERNAL_ERROR"));
        assertThat(agent.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }
}
