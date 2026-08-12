package com.insurance.platform.client.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.insurance.platform.client.dto.AgentChatRequest;
import com.insurance.platform.client.exception.AgentClientException;
import com.insurance.platform.client.exception.AgentClientException.Kind;
import com.insurance.platform.config.AgentClientConfig;
import com.insurance.platform.config.AiServiceProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpAgentClientTests {

    private static final String TRACE_ID = "01J4EXAMPLETRACE01";

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();
    private final AtomicReference<ExchangeHandler> handler = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/agent/chat", exchange -> {
            ExchangeHandler current = handler.get();
            if (current == null) {
                respond(exchange, 500, "{}");
                return;
            }
            current.handle(exchange);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsFrozenJsonAndTraceIdAndParsesSuccess() throws Exception {
        UUID requestId = UUID.randomUUID();
        AtomicReference<JsonNode> received = new AtomicReference<>();
        AtomicReference<String> trace = new AtomicReference<>();
        handler.set(exchange -> {
            received.set(objectMapper.readTree(exchange.getRequestBody()));
            trace.set(exchange.getRequestHeaders().getFirst("X-Trace-Id"));
            respond(exchange, 200, """
                    {"success":true,"data":{"requestId":"%s","answer":"真实回答",\
                    "sources":[],"durationMs":7},"error":null,"traceId":"%s"}
                    """.formatted(requestId, TRACE_ID));
        });

        var response = client(Duration.ofSeconds(1)).chat(
                request(requestId), TRACE_ID);

        assertThat(response.requestId()).isEqualTo(requestId);
        assertThat(response.answer()).isEqualTo("真实回答");
        assertThat(trace.get()).isEqualTo(TRACE_ID);
        assertThat(received.get().path("requestId").asText())
                .isEqualTo(requestId.toString());
        assertThat(received.get().path("history").isArray()).isTrue();
        List<String> fieldNames = new ArrayList<>();
        received.get().fieldNames().forEachRemaining(fieldNames::add);
        assertThat(fieldNames)
                .containsExactlyInAnyOrder(
                        "requestId", "sessionId", "message", "history");
    }

    @Test
    void parsesInternalFailureWithoutLeakingMessage() {
        handler.set(exchange -> respond(exchange, 409, """
                {"success":false,"data":null,"error":{"code":"AI_REQUEST_CONFLICT",\
                "type":"CONFLICT","message":"private detail","retryable":false},\
                "traceId":"%s"}
                """.formatted(TRACE_ID)));

        assertThatThrownBy(() -> client(Duration.ofSeconds(1)).chat(
                        request(UUID.randomUUID()), TRACE_ID))
                .isInstanceOfSatisfying(AgentClientException.class, exception -> {
                    assertThat(exception.kind()).isEqualTo(Kind.REJECTED);
                    assertThat(exception.internalCode()).isEqualTo("AI_REQUEST_CONFLICT");
                    assertThat(exception.getMessage()).doesNotContain("private detail");
                });
    }

    @Test
    void malformedOrTraceMismatchedEnvelopeFailsAsProtocolError() {
        handler.set(exchange -> respond(exchange, 200, """
                {"success":true,"data":{"requestId":"%s","answer":"answer",\
                "sources":[],"durationMs":1},"traceId":"DIFFERENT_TRACE_ID"}
                """.formatted(UUID.randomUUID())));

        assertThatThrownBy(() -> client(Duration.ofSeconds(1)).chat(
                        request(UUID.randomUUID()), TRACE_ID))
                .isInstanceOfSatisfying(AgentClientException.class, exception ->
                        assertThat(exception.kind()).isEqualTo(Kind.PROTOCOL));
    }

    @Test
    void readTimeoutIsNotRetried() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch requestArrived = new CountDownLatch(1);
        handler.set(exchange -> {
            calls.incrementAndGet();
            requestArrived.countDown();
            try {
                Thread.sleep(1000);
                respond(exchange, 200, "{}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The timed-out client is expected to close the exchange.
            }
        });

        AgentClientException exception = catchThrowableOfType(
                () -> client(Duration.ofMillis(300)).chat(
                        request(UUID.randomUUID()), TRACE_ID),
                AgentClientException.class);
        assertThat(exception.kind())
                .as("cause chain: %s", causeChain(exception))
                .isEqualTo(Kind.TIMEOUT);
        assertThat(requestArrived.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(calls).hasValue(1);
    }

    @Test
    void malformedFiveHundredResponseIsClassifiedAsUpstreamFailure() {
        handler.set(exchange -> respond(exchange, 500, "not-json"));

        assertThatThrownBy(() -> client(Duration.ofSeconds(1)).chat(
                        request(UUID.randomUUID()), TRACE_ID))
                .isInstanceOfSatisfying(AgentClientException.class, exception ->
                        assertThat(exception.kind()).isEqualTo(Kind.UPSTREAM_FAILURE));
    }

    private HttpAgentClient client(Duration readTimeout) {
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        AiServiceProperties properties = new AiServiceProperties(
                baseUrl, Duration.ofSeconds(1), readTimeout);
        return new HttpAgentClient(
                new AgentClientConfig().agentRestClient(properties),
                objectMapper);
    }

    private static AgentChatRequest request(UUID requestId) {
        return new AgentChatRequest(
                requestId,
                UUID.randomUUID(),
                "question",
                List.of());
    }

    private static String causeChain(Throwable throwable) {
        StringBuilder result = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (!result.isEmpty()) {
                result.append(" -> ");
            }
            result.append(current.getClass().getName());
            current = current.getCause();
        }
        return result.toString();
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
