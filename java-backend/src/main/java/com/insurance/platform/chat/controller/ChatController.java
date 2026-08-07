package com.insurance.platform.chat.controller;

import com.insurance.platform.chat.dto.ChatRequest;
import com.insurance.platform.chat.service.ChatService;
import com.insurance.platform.chat.vo.ChatResponse;
import com.insurance.platform.common.api.ApiResponse;
import com.insurance.platform.common.trace.TraceIdContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Java 公共同步聊天入口。 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(
            path = "/messages",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ChatResponse> chat(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody ChatRequest request) {
        String traceId = TraceIdContext.currentTraceId();
        return ApiResponse.success(
                chatService.chat(request, idempotencyKey, traceId),
                traceId);
    }
}
