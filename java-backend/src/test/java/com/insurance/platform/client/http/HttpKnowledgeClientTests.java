package com.insurance.platform.client.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.insurance.platform.client.dto.KnowledgeIndexMetadata;
import com.insurance.platform.client.exception.KnowledgeClientException;
import com.insurance.platform.config.AiServiceProperties;
import com.insurance.platform.config.KnowledgeClientConfig;
import com.insurance.platform.config.KnowledgeClientProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class HttpKnowledgeClientTests {
    private static final String TRACE_ID = "01J4EXAMPLETRACE10";
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final AtomicReference<ExchangeHandler> handler = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/knowledge/documents/index", exchange ->
                handler.get().handle(exchange));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsMultipartMetadataFileAndTraceAndValidatesResponse() {
        UUID requestId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        handler.set(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.ISO_8859_1));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            assertThat(exchange.getRequestHeaders().getFirst("X-Trace-Id")).isEqualTo(TRACE_ID);
            respond(exchange, 200, """
                    {"success":true,"data":{"requestId":"%s","documentId":"%s",\
                    "indexStatus":"INDEXED"},"error":null,"traceId":"%s"}
                    """.formatted(requestId, documentId, TRACE_ID));
        });
        KnowledgeIndexMetadata metadata = new KnowledgeIndexMetadata(
                requestId, documentId, "terms.pdf", "a".repeat(64));

        var response = client(Duration.ofSeconds(2)).indexDocument(
                metadata,
                new ByteArrayResource("%PDF-1.7".getBytes()) {
                    @Override public String getFilename() { return "controlled.pdf"; }
                },
                TRACE_ID);

        assertThat(response.documentId()).isEqualTo(documentId);
        assertThat(contentType.get()).startsWith("multipart/form-data;boundary=");
        assertThat(requestBody.get()).contains("name=\"metadata\"")
                .contains(requestId.toString())
                .contains("name=\"file\"")
                .contains("%PDF-1.7")
                .doesNotContain("C:\\");
    }

    @Test
    void mismatchedResponseIsProtocolFailure() {
        handler.set(exchange -> respond(exchange, 200, """
                {"success":true,"data":{"requestId":"%s","documentId":"%s",\
                "indexStatus":"INDEXED"},"error":null,"traceId":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), TRACE_ID)));
        KnowledgeIndexMetadata metadata = new KnowledgeIndexMetadata(
                UUID.randomUUID(), UUID.randomUUID(), "terms.pdf", "a".repeat(64));

        assertThatThrownBy(() -> client(Duration.ofSeconds(2)).indexDocument(
                        metadata, new ByteArrayResource("%PDF-".getBytes()), TRACE_ID))
                .isInstanceOfSatisfying(KnowledgeClientException.class, exception ->
                        assertThat(exception.kind())
                                .isEqualTo(KnowledgeClientException.Kind.PROTOCOL));
    }

    private HttpKnowledgeClient client(Duration readTimeout) {
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        var restClient = new KnowledgeClientConfig().knowledgeRestClient(
                new AiServiceProperties(baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(60)),
                new KnowledgeClientProperties(readTimeout));
        return new HttpKnowledgeClient(restClient, objectMapper);
    }

    private static void respond(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
