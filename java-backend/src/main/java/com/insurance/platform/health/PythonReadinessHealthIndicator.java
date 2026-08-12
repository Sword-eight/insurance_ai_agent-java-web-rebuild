package com.insurance.platform.health;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.platform.client.dto.InternalEnvelope;
import com.insurance.platform.common.trace.TraceIdContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component("pythonReadiness")
public class PythonReadinessHealthIndicator implements HealthIndicator {
    private static final String READY_PATH = "/internal/v1/health/ready";
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final JavaType envelopeType;

    public PythonReadinessHealthIndicator(
            @Qualifier("aiHealthRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.envelopeType = objectMapper.getTypeFactory()
                .constructParametricType(InternalEnvelope.class, Map.class);
    }

    @Override
    public Health health() {
        String traceId = TraceIdContext.normalizeOrGenerate(UUID.randomUUID().toString().replace("-", ""));
        boolean ready = check(traceId);
        if (!ready) ready = check(traceId);
        return ready ? Health.up().build() : Health.down().build();
    }

    private boolean check(String traceId) {
        try {
            return restClient.get()
                    .uri(READY_PATH)
                    .header(TraceIdContext.HEADER_NAME, traceId)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) return false;
                        InternalEnvelope<?> envelope = objectMapper.readValue(
                                response.getBody(), envelopeType);
                        if (envelope == null || !envelope.success()
                                || !traceId.equals(envelope.traceId())
                                || envelope.error() != null
                                || !(envelope.data() instanceof Map<?, ?> data)) {
                            return false;
                        }
                        return "UP".equals(data.get("status"));
                    });
        } catch (RestClientException exception) {
            return false;
        }
    }
}
