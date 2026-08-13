package com.insurance.platform.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.platform.config.AiHealthConfig;
import com.insurance.platform.config.AiHealthProperties;
import com.insurance.platform.config.AiServiceProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PythonReadinessHealthIndicatorTests {
    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean ready;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/health/ready", this::respond);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void readinessRetriesAtMostOnceAndAcceptsValidEnvelope() {
        ready = true;
        assertThat(indicator().health().getStatus().getCode()).isEqualTo("UP");
        assertThat(calls).hasValue(1);
    }

    @Test
    void readinessReportsDownAfterExactlyTwoFailedChecks() {
        ready = false;
        assertThat(indicator().health().getStatus().getCode()).isEqualTo("DOWN");
        assertThat(calls).hasValue(2);
    }

    private PythonReadinessHealthIndicator indicator() {
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        var client = new AiHealthConfig().aiHealthRestClient(
                new AiServiceProperties(base, Duration.ofSeconds(1), Duration.ofSeconds(60)),
                new AiHealthProperties(Duration.ofSeconds(1), Duration.ofSeconds(2)));
        return new PythonReadinessHealthIndicator(client, new ObjectMapper());
    }

    private void respond(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        String traceId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
        String body;
        int status;
        if (ready) {
            status = 200;
            body = "{\"success\":true,\"data\":{\"status\":\"UP\"},"
                    + "\"error\":null,\"traceId\":\"" + traceId + "\"}";
        } else {
            status = 503;
            body = "{}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
