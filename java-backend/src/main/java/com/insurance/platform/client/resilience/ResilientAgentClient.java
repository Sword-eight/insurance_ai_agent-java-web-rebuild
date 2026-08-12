package com.insurance.platform.client.resilience;

import com.insurance.platform.client.AgentClient;
import com.insurance.platform.client.dto.AgentChatRequest;
import com.insurance.platform.client.dto.AgentChatResponse;
import com.insurance.platform.client.exception.AgentClientException;
import com.insurance.platform.client.http.HttpAgentClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class ResilientAgentClient implements AgentClient {
    private static final Logger logger = LoggerFactory.getLogger(ResilientAgentClient.class);

    private final HttpAgentClient delegate;
    private final CircuitBreaker circuitBreaker;

    public ResilientAgentClient(
            HttpAgentClient delegate,
            @Qualifier("agentCircuitBreaker") CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public AgentChatResponse chat(AgentChatRequest request, String traceId) {
        long started = System.nanoTime();
        try {
            AgentChatResponse response = circuitBreaker.executeSupplier(
                    () -> callWithOneConnectionRetry(request, traceId));
            log(request, "SUCCEEDED", null, started);
            return response;
        } catch (CallNotPermittedException exception) {
            log(request, "REJECTED", "CIRCUIT_OPEN", started);
            throw new AgentClientException(AgentClientException.Kind.UNAVAILABLE, null, exception);
        } catch (AgentClientException exception) {
            log(request, "FAILED", exception.kind().name(), started);
            throw exception;
        }
    }

    private AgentChatResponse callWithOneConnectionRetry(
            AgentChatRequest request, String traceId) {
        try {
            return delegate.chat(request, traceId);
        } catch (AgentClientException first) {
            if (first.kind() != AgentClientException.Kind.UNAVAILABLE) throw first;
            return delegate.chat(request, traceId);
        }
    }

    private void log(AgentChatRequest request, String status, String errorCode, long started) {
        logger.info(
                "event=python_agent_call service=java requestId={} status={} durationMs={} errorCode={} breakerState={}",
                request.requestId(), status, elapsedMillis(started), value(errorCode), circuitBreaker.getState());
    }

    private static long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private static String value(String value) {
        return value == null ? "NONE" : value;
    }
}
