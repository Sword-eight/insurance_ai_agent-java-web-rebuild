package com.insurance.platform.conversation.controller;

import com.insurance.platform.common.api.ApiResponse;
import com.insurance.platform.common.api.PageResponse;
import com.insurance.platform.common.trace.TraceIdContext;
import com.insurance.platform.conversation.dto.CreateConversationRequest;
import com.insurance.platform.conversation.service.ConversationService;
import com.insurance.platform.conversation.vo.ConversationView;
import com.insurance.platform.message.service.MessageService;
import com.insurance.platform.message.vo.MessageView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    private final ConversationService conversationService;
    private final MessageService messageService;

    public ConversationController(
            ConversationService conversationService,
            MessageService messageService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConversationView> create(
            @Valid @RequestBody CreateConversationRequest request) {
        return ApiResponse.success(conversationService.create(request), traceId());
    }

    @GetMapping
    public ApiResponse<PageResponse<ConversationView>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(conversationService.list(page, size), traceId());
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationView> get(@PathVariable UUID conversationId) {
        return ApiResponse.success(conversationService.get(conversationId), traceId());
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<PageResponse<MessageView>> messages(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(messageService.list(conversationId, page, size), traceId());
    }

    private static String traceId() {
        return TraceIdContext.currentTraceId();
    }
}
