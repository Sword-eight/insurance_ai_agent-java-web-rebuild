package com.insurance.platform.client.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.insurance.platform.client.dto.KnowledgeIndexMetadata;
import com.insurance.platform.client.exception.KnowledgeClientException;
import com.insurance.platform.client.http.HttpKnowledgeClient;
import com.insurance.platform.config.AiResilienceConfig;
import com.insurance.platform.config.AiResilienceProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class ResilientKnowledgeClientTests {
    private static final String TRACE_ID = "01J4EXAMPLETRACE01";

    @Test
    void indexTimeoutIsNeverRetried() {
        HttpKnowledgeClient delegate = mock(HttpKnowledgeClient.class);
        KnowledgeIndexMetadata metadata = metadata();
        ByteArrayResource content = new ByteArrayResource("%PDF".getBytes());
        when(delegate.indexDocument(metadata, content, TRACE_ID)).thenThrow(
                new KnowledgeClientException(KnowledgeClientException.Kind.TIMEOUT, null));

        assertThatThrownBy(() -> new ResilientKnowledgeClient(delegate, breaker())
                .indexDocument(metadata, content, TRACE_ID))
                .isInstanceOfSatisfying(KnowledgeClientException.class,
                        exception -> assertThat(exception.kind())
                                .isEqualTo(KnowledgeClientException.Kind.TIMEOUT));
        verify(delegate).indexDocument(metadata, content, TRACE_ID);
    }

    @Test
    void openCircuitPreventsDuplicateIndexSubmission() {
        HttpKnowledgeClient delegate = mock(HttpKnowledgeClient.class);
        CircuitBreaker breaker = breaker();
        breaker.transitionToOpenState();
        KnowledgeIndexMetadata metadata = metadata();
        ByteArrayResource content = new ByteArrayResource("%PDF".getBytes());

        assertThatThrownBy(() -> new ResilientKnowledgeClient(delegate, breaker)
                .indexDocument(metadata, content, TRACE_ID))
                .isInstanceOfSatisfying(KnowledgeClientException.class,
                        exception -> assertThat(exception.kind())
                                .isEqualTo(KnowledgeClientException.Kind.UNAVAILABLE));
        verify(delegate, times(0)).indexDocument(metadata, content, TRACE_ID);
    }

    private static CircuitBreaker breaker() {
        return new AiResilienceConfig().knowledgeCircuitBreaker(new AiResilienceProperties(
                20, 10, 50, Duration.ofSeconds(30), 3));
    }

    private static KnowledgeIndexMetadata metadata() {
        return new KnowledgeIndexMetadata(
                UUID.randomUUID(), UUID.randomUUID(), "terms.pdf", "a".repeat(64));
    }
}
