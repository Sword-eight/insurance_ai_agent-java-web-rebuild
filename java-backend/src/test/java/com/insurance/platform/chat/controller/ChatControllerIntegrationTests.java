package com.insurance.platform.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.insurance.platform.chat.service.ChatService;
import com.insurance.platform.chat.vo.ChatResponse;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import com.insurance.platform.common.exception.RateLimitExceededException;
import com.insurance.platform.common.trace.TraceIdContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "00000000-0000-0000-0000-000000000001")
class ChatControllerIntegrationTests {

    private static final String TRACE_ID = "01J4EXAMPLETRACE01";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @Test
    void successReturnsFrozenPublicEnvelopeAndTraceId() throws Exception {
        UUID key = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        ChatResponse response = new ChatResponse(
                conversationId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "answer",
                List.of());
        when(chatService.chat(any(), eq(key), eq(TRACE_ID))).thenReturn(response);

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header(TraceIdContext.HEADER_NAME, TRACE_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"%s","message":"question"}
                                """.formatted(conversationId)))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.HEADER_NAME, TRACE_ID))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.conversationId")
                        .value(conversationId.toString()))
                .andExpect(jsonPath("$.data.answer").value("answer"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void missingIdempotencyKeyReturnsValidationErrorWithoutCallingService()
            throws Exception {
        mockMvc.perform(post("/api/v1/chat/messages")
                        .header(TraceIdContext.HEADER_NAME, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"%s","message":"question"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(chatService);
    }

    @Test
    void invalidMessageReturnsValidationErrorWithoutCallingService()
            throws Exception {
        mockMvc.perform(post("/api/v1/chat/messages")
                        .header(TraceIdContext.HEADER_NAME, TRACE_ID)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"%s","message":"   "}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(chatService);
    }

    @Test
    void serviceFailureUsesFrozenPublicError() throws Exception {
        UUID key = UUID.randomUUID();
        when(chatService.chat(any(), eq(key), eq(TRACE_ID)))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT));

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header(TraceIdContext.HEADER_NAME, TRACE_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"%s","message":"question"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("AI_SERVICE_TIMEOUT"))
                .andExpect(jsonPath("$.message").value("AI service timed out"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void rateLimitFailureReturnsRetryAfterHeader() throws Exception {
        UUID key = UUID.randomUUID();
        when(chatService.chat(any(), eq(key), eq(TRACE_ID)))
                .thenThrow(new RateLimitExceededException(23));

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header(TraceIdContext.HEADER_NAME, TRACE_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"%s","message":"question"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "23"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }
}
