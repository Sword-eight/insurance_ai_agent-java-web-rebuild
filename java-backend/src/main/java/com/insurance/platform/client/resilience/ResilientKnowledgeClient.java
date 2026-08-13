package com.insurance.platform.client.resilience;

import com.insurance.platform.client.KnowledgeClient;
import com.insurance.platform.client.dto.KnowledgeIndexMetadata;
import com.insurance.platform.client.dto.KnowledgeIndexResponse;
import com.insurance.platform.client.exception.KnowledgeClientException;
import com.insurance.platform.client.http.HttpKnowledgeClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Primary
@Component
public class ResilientKnowledgeClient implements KnowledgeClient {
    private static final Logger logger = LoggerFactory.getLogger(ResilientKnowledgeClient.class);

    private final HttpKnowledgeClient delegate;
    private final CircuitBreaker circuitBreaker;

    public ResilientKnowledgeClient(
            HttpKnowledgeClient delegate,
            @Qualifier("knowledgeCircuitBreaker") CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public KnowledgeIndexResponse indexDocument(
            KnowledgeIndexMetadata metadata, Resource content, String traceId) {
        long started = System.nanoTime();
        try {
            KnowledgeIndexResponse response = circuitBreaker.executeSupplier(
                    () -> delegate.indexDocument(metadata, content, traceId));
            log(metadata, "SUCCEEDED", null, started);
            return response;
        } catch (CallNotPermittedException exception) {
            log(metadata, "REJECTED", "CIRCUIT_OPEN", started);
            throw new KnowledgeClientException(KnowledgeClientException.Kind.UNAVAILABLE, null, exception);
        } catch (KnowledgeClientException exception) {
            log(metadata, "FAILED", exception.kind().name(), started);
            throw exception;
        }
    }

    @Override
    public void rebuild(String traceId) {
        execute(() -> { delegate.rebuild(traceId); return null; });
    }

    @Override
    public Map<String, Object> status(String traceId) {
        return execute(() -> delegate.status(traceId));
    }

    private <T> T execute(java.util.function.Supplier<T> call) {
        try {
            return circuitBreaker.executeSupplier(call);
        } catch (CallNotPermittedException exception) {
            throw new KnowledgeClientException(KnowledgeClientException.Kind.UNAVAILABLE, null, exception);
        }
    }

    private void log(KnowledgeIndexMetadata metadata, String status, String errorCode, long started) {
        logger.info(
                "event=python_knowledge_call service=java requestId={} documentId={} status={} durationMs={} errorCode={} breakerState={}",
                metadata.requestId(), metadata.documentId(), status, elapsedMillis(started),
                errorCode == null ? "NONE" : errorCode, circuitBreaker.getState());
    }

    private static long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }
}
