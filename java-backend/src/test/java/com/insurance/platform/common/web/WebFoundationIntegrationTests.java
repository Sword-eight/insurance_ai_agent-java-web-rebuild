package com.insurance.platform.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.platform.common.api.ApiResponse;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import com.insurance.platform.common.trace.TraceIdContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
        WebFoundationIntegrationTests.FoundationTestController.class,
        WebFoundationIntegrationTests.InternalTestController.class
})
class WebFoundationIntegrationTests {

    private static final String TRACE_ID = "01J4EXAMPLETRACE01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FoundationTestController controller;

    @BeforeEach
    void resetState() {
        controller.reset();
        MDC.clear();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void validRequestReturnsFrozenEnvelopeAndOriginalTraceId() throws Exception {
        mockMvc.perform(post("/api/v1/foundation-test")
                        .header(TraceIdContext.HEADER_NAME, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"medical\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.HEADER_NAME, TRACE_ID))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.name").value("medical"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.timestamp").isString());

        assertThat(controller.invocations()).isEqualTo(1);
        assertThat(MDC.get(TraceIdContext.MDC_KEY)).isNull();
    }

    @Test
    void missingTraceIdGeneratesOneAndKeepsHeaderAndBodyEqual() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/foundation-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"medical\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String headerTraceId = result.getResponse()
                .getHeader(TraceIdContext.HEADER_NAME);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(TraceIdContext.isValid(headerTraceId)).isTrue();
        assertThat(body.path("traceId").asText()).isEqualTo(headerTraceId);
    }

    @Test
    void invalidTraceIdIsReplaced() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/foundation-test")
                        .header(TraceIdContext.HEADER_NAME, "bad trace id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"medical\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String replacement = result.getResponse()
                .getHeader(TraceIdContext.HEADER_NAME);
        assertThat(replacement).isNotEqualTo("bad trace id");
        assertThat(TraceIdContext.isValid(replacement)).isTrue();
    }

    @Test
    void beanValidationFailsBeforeControllerMethod() throws Exception {
        mockMvc.perform(post("/api/v1/foundation-test")
                        .header(TraceIdContext.HEADER_NAME, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("request validation failed"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));

        assertThat(controller.invocations()).isZero();
    }

    @Test
    void malformedJsonUsesStableValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/foundation-test")
                        .header(TraceIdContext.HEADER_NAME, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void businessExceptionKeepsFrozenCodeAndHttpStatus() throws Exception {
        mockMvc.perform(get("/api/v1/foundation-test/business")
                        .header(TraceIdContext.HEADER_NAME, TRACE_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.message").value("same key has different payload"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void unexpectedExceptionReturnsSafeErrorWithoutLeakingDetails() throws Exception {
        Logger handlerLogger = (Logger) LoggerFactory.getLogger(
                "com.insurance.platform.common.exception.GlobalExceptionHandler");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
        MvcResult result;
        try {
            result = mockMvc.perform(get("/api/v1/foundation-test/unexpected")
                            .header(TraceIdContext.HEADER_NAME, TRACE_ID))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("internal server error"))
                    .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                    .andReturn();
        } finally {
            handlerLogger.detachAppender(appender);
            appender.stop();
        }

        String body = result.getResponse().getContentAsString();
        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(body)
                .doesNotContain("private")
                .doesNotContain("token.txt")
                .doesNotContain("RuntimeException");
        assertThat(logs)
                .contains(TRACE_ID)
                .contains("RuntimeException")
                .doesNotContain("private")
                .doesNotContain("token.txt");
        assertThat(MDC.get(TraceIdContext.MDC_KEY)).isNull();
    }

    @Test
    void unsupportedContentTypeUsesFrozenMediaError() throws Exception {
        mockMvc.perform(post("/api/v1/foundation-test")
                        .header(TraceIdContext.HEADER_NAME, TRACE_ID)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("medical"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void publicOpenApiContainsOnlyPublicV1Paths() throws Exception {
        assertOnlyPublicPaths("/v3/api-docs/public-v1");
        assertOnlyPublicPaths("/v3/api-docs");
    }

    private void assertOnlyPublicPaths(String documentationPath) throws Exception {
        MvcResult result = mockMvc.perform(get(documentationPath))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode document = objectMapper.readTree(
                result.getResponse().getContentAsByteArray());
        JsonNode paths = document.path("paths");
        assertThat(document.path("info").path("title").asText())
                .isEqualTo("Insurance AI Platform API");
        assertThat(document.path("info").path("version").asText())
                .isEqualTo("v1");
        assertThat(paths.has("/api/v1/foundation-test")).isTrue();
        assertThat(paths.has("/internal/foundation-test")).isFalse();
        paths.fieldNames().forEachRemaining(
                path -> assertThat(path).startsWith("/api/v1/"));
    }

    record FoundationRequest(
            @NotBlank @Size(max = 20) String name) {
    }

    @RestController
    @RequestMapping("/api/v1/foundation-test")
    static class FoundationTestController {

        private final AtomicInteger invocationCount = new AtomicInteger();

        @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
        ApiResponse<Map<String, String>> validate(
                @Valid @RequestBody FoundationRequest request) {
            invocationCount.incrementAndGet();
            return ApiResponse.success(
                    Map.of("name", request.name()),
                    TraceIdContext.currentTraceId());
        }

        @GetMapping("/business")
        ApiResponse<Void> businessFailure() {
            throw new BusinessException(
                    ErrorCode.CHAT_IDEMPOTENCY_CONFLICT,
                    "same key has different payload");
        }

        @GetMapping("/unexpected")
        ApiResponse<Void> unexpectedFailure() {
            throw new RuntimeException("secret at C:\\private\\token.txt");
        }

        int invocations() {
            return invocationCount.get();
        }

        void reset() {
            invocationCount.set(0);
        }
    }

    @RestController
    @RequestMapping("/internal/foundation-test")
    static class InternalTestController {

        @GetMapping
        Map<String, String> internalOnly() {
            return Map.of("status", "internal");
        }
    }
}
