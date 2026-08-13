package com.insurance.platform.config;

import com.insurance.platform.client.exception.AgentClientException;
import com.insurance.platform.client.exception.KnowledgeClientException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.util.function.Predicate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiResilienceProperties.class)
public class AiResilienceConfig {

    @Bean("agentCircuitBreaker")
    public CircuitBreaker agentCircuitBreaker(AiResilienceProperties properties) {
        return CircuitBreaker.of("agentClient", config(properties, AiResilienceConfig::recordAgentFailure));
    }

    @Bean("knowledgeCircuitBreaker")
    public CircuitBreaker knowledgeCircuitBreaker(AiResilienceProperties properties) {
        return CircuitBreaker.of("knowledgeClient", config(properties, AiResilienceConfig::recordKnowledgeFailure));
    }

    private static CircuitBreakerConfig config(
            AiResilienceProperties properties,
            Predicate<Throwable> recordFailure) {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.slidingWindowSize())
                .minimumNumberOfCalls(properties.minimumNumberOfCalls())
                .failureRateThreshold(properties.failureRateThreshold())
                .waitDurationInOpenState(properties.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.permittedCallsInHalfOpenState())
                .recordException(recordFailure)
                .build();
    }

    private static boolean recordAgentFailure(Throwable throwable) {
        return throwable instanceof AgentClientException exception
                && switch (exception.kind()) {
                    case TIMEOUT, UNAVAILABLE, DELIVERY_UNKNOWN, UPSTREAM_FAILURE -> true;
                    case REJECTED, PROTOCOL -> false;
                };
    }

    private static boolean recordKnowledgeFailure(Throwable throwable) {
        return throwable instanceof KnowledgeClientException exception
                && switch (exception.kind()) {
                    case TIMEOUT, UNAVAILABLE, DELIVERY_UNKNOWN, UPSTREAM_FAILURE -> true;
                    case REJECTED, PROTOCOL -> false;
                };
    }
}
