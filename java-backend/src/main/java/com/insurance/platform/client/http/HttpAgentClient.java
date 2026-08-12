package com.insurance.platform.client.http;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.platform.client.AgentClient;
import com.insurance.platform.client.dto.AgentChatRequest;
import com.insurance.platform.client.dto.AgentChatResponse;
import com.insurance.platform.client.dto.InternalEnvelope;
import com.insurance.platform.client.exception.AgentClientException;
import com.insurance.platform.client.exception.AgentClientException.Kind;
import com.insurance.platform.common.trace.TraceIdContext;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Java → Python /internal/v1/agent/chat 同步 HTTP 适配器。 */
@Component
public class HttpAgentClient implements AgentClient {

    private static final String CHAT_PATH = "/internal/v1/agent/chat";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final JavaType envelopeType;

    public HttpAgentClient(
            @Qualifier("agentRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.envelopeType = objectMapper.getTypeFactory()
                .constructParametricType(InternalEnvelope.class, AgentChatResponse.class);
    }

    @Override
    public AgentChatResponse chat(AgentChatRequest request, String traceId) {
        try {
            return restClient.post()
                    .uri(CHAT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(TraceIdContext.HEADER_NAME, traceId)
                    .body(request)
                    .exchange((httpRequest, response) -> parseResponse(response, traceId));
        } catch (AgentClientException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw classifyResourceFailure(exception);
        } catch (RestClientException exception) {
            throw new AgentClientException(Kind.PROTOCOL, null, exception);
        }
    }

    private AgentChatResponse parseResponse(
            org.springframework.http.client.ClientHttpResponse response,
            String expectedTraceId) throws IOException {
        InternalEnvelope<AgentChatResponse> envelope;
        try {
            envelope = objectMapper.readValue(response.getBody(), envelopeType);
        } catch (IOException exception) {
            throw new AgentClientException(kindForInvalidResponse(response), null, exception);
        }
        if (envelope == null || !expectedTraceId.equals(envelope.traceId())) {
            throw new AgentClientException(kindForInvalidResponse(response), null);
        }
        if (!envelope.success()) {
            if (envelope.error() == null || envelope.error().code() == null) {
                throw new AgentClientException(kindForInvalidResponse(response), null);
            }
            Kind kind = response.getStatusCode().is5xxServerError()
                    ? Kind.UPSTREAM_FAILURE : Kind.REJECTED;
            throw new AgentClientException(kind, envelope.error().code());
        }
        if (!response.getStatusCode().is2xxSuccessful()
                || envelope.data() == null
                || envelope.error() != null) {
            throw new AgentClientException(kindForInvalidResponse(response), null);
        }
        return envelope.data();
    }

    private static Kind kindForInvalidResponse(
            org.springframework.http.client.ClientHttpResponse response) throws IOException {
        return response.getStatusCode().is5xxServerError() ? Kind.UPSTREAM_FAILURE : Kind.PROTOCOL;
    }

    private static AgentClientException classifyResourceFailure(
            ResourceAccessException exception) {
        if (hasCause(exception, HttpConnectTimeoutException.class)
                || hasCause(exception, ConnectException.class)
                || hasCause(exception, UnknownHostException.class)
                || hasCause(exception, NoRouteToHostException.class)) {
            return new AgentClientException(Kind.UNAVAILABLE, null, exception);
        }
        if (hasCause(exception, HttpTimeoutException.class)
                || hasCause(exception, SocketTimeoutException.class)
                || hasCause(exception, TimeoutException.class)) {
            return new AgentClientException(Kind.TIMEOUT, null, exception);
        }
        return new AgentClientException(Kind.DELIVERY_UNKNOWN, null, exception);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
