package com.insurance.platform.client.http;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.platform.client.KnowledgeClient;
import com.insurance.platform.client.dto.InternalEnvelope;
import com.insurance.platform.client.dto.KnowledgeIndexMetadata;
import com.insurance.platform.client.dto.KnowledgeIndexResponse;
import com.insurance.platform.client.exception.KnowledgeClientException;
import com.insurance.platform.client.exception.KnowledgeClientException.Kind;
import com.insurance.platform.common.trace.TraceIdContext;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpKnowledgeClient implements KnowledgeClient {
    private static final String INDEX_PATH = "/internal/v1/knowledge/documents/index";
    private static final String REBUILD_PATH = "/internal/v1/knowledge/rebuild";
    private static final String STATUS_PATH = "/internal/v1/knowledge/status";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final JavaType indexEnvelopeType;
    private final JavaType mapEnvelopeType;

    public HttpKnowledgeClient(
            @Qualifier("knowledgeRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.indexEnvelopeType = objectMapper.getTypeFactory()
                .constructParametricType(InternalEnvelope.class, KnowledgeIndexResponse.class);
        this.mapEnvelopeType = objectMapper.getTypeFactory()
                .constructParametricType(InternalEnvelope.class, Map.class);
    }

    @Override
    public KnowledgeIndexResponse indexDocument(
            KnowledgeIndexMetadata metadata, Resource content, String traceId) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        try {
            body.part("metadata", objectMapper.writeValueAsString(metadata))
                    .contentType(MediaType.APPLICATION_JSON);
        } catch (IOException exception) {
            throw new KnowledgeClientException(Kind.PROTOCOL, null, exception);
        }
        body.part("file", content).contentType(MediaType.APPLICATION_PDF);
        Object parsed = execute(() -> restClient.post()
                .uri(INDEX_PATH)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header(TraceIdContext.HEADER_NAME, traceId)
                .body(body.build())
                .exchange((request, response) -> parse(response, indexEnvelopeType, traceId)));
        KnowledgeIndexResponse result = (KnowledgeIndexResponse) parsed;
        if (!metadata.requestId().equals(result.requestId())
                || !metadata.documentId().equals(result.documentId())
                || !"INDEXED".equals(result.indexStatus())) {
            throw new KnowledgeClientException(Kind.PROTOCOL, null);
        }
        return result;
    }

    @Override
    public void rebuild(String traceId) {
        execute(() -> restClient.post().uri(REBUILD_PATH)
                .header(TraceIdContext.HEADER_NAME, traceId)
                .exchange((request, response) -> parse(response, mapEnvelopeType, traceId)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> status(String traceId) {
        return (Map<String, Object>) execute(() -> restClient.get().uri(STATUS_PATH)
                .header(TraceIdContext.HEADER_NAME, traceId)
                .exchange((request, response) -> parse(response, mapEnvelopeType, traceId)));
    }

    private Object execute(java.util.concurrent.Callable<Object> call) {
        try {
            return call.call();
        } catch (KnowledgeClientException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw classifyResourceFailure(exception);
        } catch (RestClientException exception) {
            throw new KnowledgeClientException(Kind.PROTOCOL, null, exception);
        } catch (Exception exception) {
            throw new KnowledgeClientException(Kind.PROTOCOL, null, exception);
        }
    }

    private Object parse(
            org.springframework.http.client.ClientHttpResponse response,
            JavaType type,
            String expectedTraceId) throws IOException {
        InternalEnvelope<?> envelope;
        try {
            envelope = objectMapper.readValue(response.getBody(), type);
        } catch (IOException exception) {
            throw new KnowledgeClientException(Kind.PROTOCOL, null, exception);
        }
        if (envelope == null || !expectedTraceId.equals(envelope.traceId())) {
            throw new KnowledgeClientException(Kind.PROTOCOL, null);
        }
        if (!envelope.success()) {
            if (envelope.error() == null || envelope.error().code() == null) {
                throw new KnowledgeClientException(Kind.PROTOCOL, null);
            }
            throw new KnowledgeClientException(Kind.REJECTED, envelope.error().code());
        }
        if (!response.getStatusCode().is2xxSuccessful()
                || envelope.data() == null || envelope.error() != null) {
            throw new KnowledgeClientException(Kind.PROTOCOL, null);
        }
        return envelope.data();
    }

    private static KnowledgeClientException classifyResourceFailure(ResourceAccessException exception) {
        if (hasCause(exception, HttpConnectTimeoutException.class)
                || hasCause(exception, ConnectException.class)
                || hasCause(exception, UnknownHostException.class)
                || hasCause(exception, NoRouteToHostException.class)) {
            return new KnowledgeClientException(Kind.UNAVAILABLE, null, exception);
        }
        if (hasCause(exception, HttpTimeoutException.class)
                || hasCause(exception, SocketTimeoutException.class)
                || hasCause(exception, TimeoutException.class)) {
            return new KnowledgeClientException(Kind.TIMEOUT, null, exception);
        }
        return new KnowledgeClientException(Kind.DELIVERY_UNKNOWN, null, exception);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }
}
