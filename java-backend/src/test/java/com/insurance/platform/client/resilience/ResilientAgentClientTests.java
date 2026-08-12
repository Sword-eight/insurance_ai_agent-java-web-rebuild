package com.insurance.platform.client.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.insurance.platform.client.dto.AgentChatRequest;
import com.insurance.platform.client.dto.AgentChatResponse;
import com.insurance.platform.client.exception.AgentClientException;
import com.insurance.platform.client.http.HttpAgentClient;
import com.insurance.platform.config.AiResilienceConfig;
import com.insurance.platform.config.AiResilienceProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResilientAgentClientTests {
    private static final String TRACE_ID = "01J4EXAMPLETRACE01";

    @Test
    void retriesExactlyOnceOnlyForProvenConnectionFailureWithSameRequest() {
        HttpAgentClient delegate = mock(HttpAgentClient.class);
        CircuitBreaker breaker = breaker();
        AgentChatRequest request = request();
        AgentChatResponse expected = new AgentChatResponse(request.requestId(), "answer", List.of(), 1);
        when(delegate.chat(request, TRACE_ID))
                .thenThrow(new AgentClientException(AgentClientException.Kind.UNAVAILABLE, null))
                .thenReturn(expected);

        AgentChatResponse actual = new ResilientAgentClient(delegate, breaker)
                .chat(request, TRACE_ID);

        assertThat(actual).isSameAs(expected);
        verify(delegate, times(2)).chat(request, TRACE_ID);
        assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void neverRetriesReadTimeoutOrDeliveryUnknown() {
        for (AgentClientException.Kind kind : List.of(
                AgentClientException.Kind.TIMEOUT,
                AgentClientException.Kind.DELIVERY_UNKNOWN)) {
            HttpAgentClient delegate = mock(HttpAgentClient.class);
            AgentChatRequest request = request();
            when(delegate.chat(request, TRACE_ID)).thenThrow(new AgentClientException(kind, null));

            assertThatThrownBy(() -> new ResilientAgentClient(delegate, breaker())
                    .chat(request, TRACE_ID))
                    .isInstanceOfSatisfying(AgentClientException.class,
                            exception -> assertThat(exception.kind()).isEqualTo(kind));
            verify(delegate).chat(request, TRACE_ID);
        }
    }

    @Test
    void openCircuitRejectsWithoutCallingPython() {
        HttpAgentClient delegate = mock(HttpAgentClient.class);
        CircuitBreaker breaker = breaker();
        breaker.transitionToOpenState();
        AgentChatRequest request = request();

        assertThatThrownBy(() -> new ResilientAgentClient(delegate, breaker)
                .chat(request, TRACE_ID))
                .isInstanceOfSatisfying(AgentClientException.class,
                        exception -> assertThat(exception.kind())
                                .isEqualTo(AgentClientException.Kind.UNAVAILABLE));
        verify(delegate, times(0)).chat(request, TRACE_ID);
    }

    private static CircuitBreaker breaker() {
        return new AiResilienceConfig().agentCircuitBreaker(new AiResilienceProperties(
                20, 10, 50, Duration.ofSeconds(30), 3));
    }

    private static AgentChatRequest request() {
        return new AgentChatRequest(UUID.randomUUID(), UUID.randomUUID(), "question", List.of());
    }
}
