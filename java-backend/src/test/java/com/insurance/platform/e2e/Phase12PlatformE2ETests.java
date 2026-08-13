package com.insurance.platform.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.platform.redis.RedisKeyFactory;
import com.insurance.platform.testsupport.TestJwtConfiguration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestJwtConfiguration.class)
@Testcontainers
class Phase12PlatformE2ETests {
    private static final String TRACE_ID = "01J4PHASE12E2ETRACE";
    private static final String USERNAME = "phase12_e2e_user";
    private static final String PASSWORD = "Phase12Pass123!";
    private static final byte[] PDF = ("%PDF-1.7\n"
            + "% Phase 12 controlled integration fixture\n"
            + "1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n")
            .getBytes(StandardCharsets.UTF_8);
    private static final Path REPOSITORY_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path JAVA_DOCUMENT_DIR = Path.of("target", "phase12-java-docs")
            .toAbsolutePath().normalize();
    private static final Path PYTHON_DOCUMENT_DIR = Path.of("target", "phase12-python-docs")
            .toAbsolutePath().normalize();
    private static Process pythonProcess;
    private static int pythonPort;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("phase12_platform")
            .withUsername("phase12_user")
            .withPassword(UUID.randomUUID().toString())
            .withEnv("MYSQL_ROOT_PASSWORD", UUID.randomUUID().toString())
            .withTmpFs(Map.of("/var/lib/mysql", "rw"))
            .withStartupTimeout(Duration.ofMinutes(5));

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("insurance.redis.environment", () -> "phase12-e2e");
        registry.add("insurance.ai-service.base-url", Phase12PlatformE2ETests::pythonBaseUrl);
        registry.add("insurance.ai-service.read-timeout", () -> "500ms");
        registry.add("insurance.document-storage.root", JAVA_DOCUMENT_DIR::toString);
    }

    @LocalServerPort private int javaPort;
    @Autowired private TestRestTemplate rest;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private RedisKeyFactory keys;

    @AfterAll
    static void stopPython() throws Exception {
        if (pythonProcess != null) {
            pythonProcess.destroy();
            if (!pythonProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                pythonProcess.destroyForcibly();
                pythonProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
        deleteTestDirectory(JAVA_DOCUMENT_DIR);
        deleteTestDirectory(PYTHON_DOCUMENT_DIR);
    }

    @Test
    void browserFacingPlatformUsesRealHttpMySqlRedisAndPythonContracts() throws Exception {
        JsonNode registered = postJson("/api/v1/auth/register", """
                {"username":"%s","password":"%s"}
                """.formatted(USERNAME, PASSWORD), null, null, 201);
        UUID userId = UUID.fromString(registered.path("data").path("userId").asText());

        JsonNode loggedIn = postJson("/api/v1/auth/login", """
                {"username":"%s","password":"%s"}
                """.formatted(USERNAME, PASSWORD), null, null, 200);
        String bearer = "Bearer " + loggedIn.path("data").path("accessToken").asText();

        JsonNode conversation = postJson("/api/v1/conversations", """
                {"title":"Phase 12 final E2E"}
                """, bearer, null, 201);
        String conversationId = conversation.path("data").path("conversationId").asText();
        UUID idempotencyKey = UUID.randomUUID();
        String chatBody = """
                {"conversationId":"%s","message":"等待期一般有多久？"}
                """.formatted(conversationId);

        JsonNode chat = postJson(
                "/api/v1/chat/messages", chatBody, bearer, idempotencyKey, 200);
        JsonNode replay = postJson(
                "/api/v1/chat/messages", chatBody, bearer, idempotencyKey, 200);

        assertThat(chat.path("traceId").asText()).isEqualTo(TRACE_ID);
        assertThat(chat.path("data").path("answer").asText())
                .isEqualTo("Phase 12 E2E：等待期一般有多久？");
        assertThat(replay.path("data")).isEqualTo(chat.path("data"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iap_chat_request", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iap_chat_message", Long.class))
                .isEqualTo(2L);
        String idempotencyRedisKey = keys.chatIdempotency(userId, idempotencyKey);
        assertThat(redis.opsForValue().get(idempotencyRedisKey)).contains("SUCCEEDED");
        assertThat(redis.getExpire(idempotencyRedisKey)).isBetween(1L, 86_400L);

        JsonNode messages = getJson(
                "/api/v1/conversations/" + conversationId + "/messages", bearer, 200);
        assertThat(messages.path("data").path("total").asLong()).isEqualTo(2);

        JsonNode document = uploadPdf(bearer);
        assertThat(document.path("data").path("indexStatus").asText()).isEqualTo("INDEXED");
        assertThat(jdbc.queryForObject(
                "SELECT index_status FROM iap_knowledge_document", String.class))
                .isEqualTo("INDEXED");

        postJson("/api/v1/chat/messages", """
                {"conversationId":"%s","message":"PHASE12_UNAVAILABLE"}
                """.formatted(conversationId), bearer, UUID.randomUUID(), 503);
        postJson("/api/v1/chat/messages", """
                {"conversationId":"%s","message":"PHASE12_TIMEOUT"}
                """.formatted(conversationId), bearer, UUID.randomUUID(), 504);
        assertThat(jdbc.queryForList(
                "SELECT status FROM iap_chat_request ORDER BY id").stream()
                .map(row -> row.get("status")))
                .containsExactly("SUCCEEDED", "FAILED", "UNKNOWN");
    }

    private JsonNode postJson(
            String path,
            String body,
            String bearer,
            UUID idempotencyKey,
            int expectedStatus) throws Exception {
        HttpHeaders headers = headers(bearer);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) headers.set("Idempotency-Key", idempotencyKey.toString());
        ResponseEntity<String> response = rest.exchange(
                url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        assertThat(response.getHeaders().getFirst("X-Trace-Id")).isEqualTo(TRACE_ID);
        return objectMapper.readTree(response.getBody());
    }

    private JsonNode getJson(String path, String bearer, int expectedStatus) throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url(path), HttpMethod.GET, new HttpEntity<>(headers(bearer)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        return objectMapper.readTree(response.getBody());
    }

    private JsonNode uploadPdf(String bearer) throws Exception {
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(PDF) {
            @Override public String getFilename() { return "phase12-terms.pdf"; }
        });
        HttpHeaders headers = headers(bearer);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/documents"), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return objectMapper.readTree(response.getBody());
    }

    private HttpHeaders headers(String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", TRACE_ID);
        if (bearer != null) headers.set(HttpHeaders.AUTHORIZATION, bearer);
        return headers;
    }

    private String url(String path) {
        return "http://127.0.0.1:" + javaPort + path;
    }

    private static synchronized String pythonBaseUrl() {
        if (pythonProcess == null) startPython();
        return "http://127.0.0.1:" + pythonPort;
    }

    private static void startPython() {
        try {
            Files.createDirectories(PYTHON_DOCUMENT_DIR);
            pythonPort = freePort();
            String executable = System.getProperty("phase12.python.executable", "python");
            ProcessBuilder builder = new ProcessBuilder(
                    executable,
                    "-m", "uvicorn",
                    "tests.support.phase12_e2e_api:app",
                    "--host", "127.0.0.1",
                    "--port", Integer.toString(pythonPort));
            builder.directory(REPOSITORY_ROOT.toFile());
            builder.environment().put(
                    "PHASE12_PYTHON_DOCUMENT_DIR", PYTHON_DOCUMENT_DIR.toString());
            builder.redirectErrorStream(true);
            builder.redirectOutput(Path.of("target", "phase12-python.log").toFile());
            pythonProcess = builder.start();
            awaitPythonReady();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to start Phase 12 Python runtime", exception);
        }
    }

    private static void awaitPythonReady() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(300)).build();
        URI ready = URI.create("http://127.0.0.1:" + pythonPort + "/internal/v1/health/ready");
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (!pythonProcess.isAlive()) {
                throw new IllegalStateException("Phase 12 Python runtime exited during startup");
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(ready)
                        .timeout(Duration.ofSeconds(1))
                        .header("X-Trace-Id", TRACE_ID)
                        .GET().build();
                if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // Process may still be binding the local port.
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Phase 12 Python readiness timed out");
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }

    private static void deleteTestDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) Files.deleteIfExists(file);
        }
        Files.deleteIfExists(directory);
    }
}
