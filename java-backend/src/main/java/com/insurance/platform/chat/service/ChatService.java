package com.insurance.platform.chat.service;

import com.insurance.platform.chat.dto.ChatRequest;
import com.insurance.platform.chat.vo.ChatResponse;
import com.insurance.platform.chat.vo.ChatSource;
import com.insurance.platform.client.AgentClient;
import com.insurance.platform.client.dto.AgentChatRequest;
import com.insurance.platform.client.dto.AgentChatResponse;
import com.insurance.platform.client.exception.AgentClientException;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 无数据库 Phase 6 聊天编排；持久状态和用户归属属于 Phase 7/9。 */
@Service
public class ChatService {

    private final AgentClient agentClient;

    public ChatService(AgentClient agentClient) {
        this.agentClient = agentClient;
    }

    public ChatResponse chat(
            ChatRequest request,
            UUID idempotencyKey,
            String traceId) {
        UUID requestId = stableId("phase6:request:", idempotencyKey);
        AgentChatRequest internalRequest = new AgentChatRequest(
                requestId,
                request.conversationId(),
                request.message(),
                List.of());

        AgentChatResponse internalResponse;
        try {
            internalResponse = agentClient.chat(internalRequest, traceId);
        } catch (AgentClientException exception) {
            throw mapClientFailure(exception);
        }
        if (internalResponse == null
                || !requestId.equals(internalResponse.requestId())
                || internalResponse.answer() == null
                || internalResponse.answer().isBlank()
                || internalResponse.durationMs() < 0) {
            throw new BusinessException(ErrorCode.AI_EXECUTION_FAILED);
        }

        return new ChatResponse(
                request.conversationId(),
                requestId,
                stableId("phase6:user-message:", requestId),
                stableId("phase6:assistant-message:", requestId),
                internalResponse.answer().trim(),
                mapSources(internalResponse.sources()));
    }

    private static List<ChatSource> mapSources(List<Map<String, Object>> sources) {
        if (sources == null) {
            return List.of();
        }
        try {
            return sources.stream().map(ChatService::mapSource).toList();
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AI_EXECUTION_FAILED);
        }
    }

    private static ChatSource mapSource(Map<String, Object> source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        String documentName = requiredString(source.get("documentName"));
        String snippet = requiredString(source.get("snippet"));
        if (snippet.length() > 500) {
            throw new IllegalArgumentException("source snippet is too long");
        }
        Integer page = optionalInteger(source.get("page"));
        Double score = optionalDouble(source.get("score"));
        return new ChatSource(documentName, page, snippet, score);
    }

    private static String requiredString(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("source text is invalid");
        }
        return text.trim();
    }

    private static Integer optionalInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("source page is invalid");
        }
        return number.intValue();
    }

    private static Double optionalDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("source score is invalid");
        }
        return number.doubleValue();
    }

    private static BusinessException mapClientFailure(AgentClientException exception) {
        return switch (exception.kind()) {
            case TIMEOUT -> new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT);
            case UNAVAILABLE -> new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            case PROTOCOL -> new BusinessException(ErrorCode.AI_EXECUTION_FAILED);
            case REJECTED -> mapRejectedCode(exception.internalCode());
        };
    }

    private static BusinessException mapRejectedCode(String internalCode) {
        if ("AI_REQUEST_IN_PROGRESS".equals(internalCode)) {
            return new BusinessException(ErrorCode.CHAT_REQUEST_IN_PROGRESS);
        }
        if ("AI_REQUEST_CONFLICT".equals(internalCode)) {
            return new BusinessException(ErrorCode.CHAT_IDEMPOTENCY_CONFLICT);
        }
        if ("AI_LLM_UNAVAILABLE".equals(internalCode)) {
            return new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
        if ("AI_LLM_TIMEOUT".equals(internalCode)) {
            return new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT);
        }
        return new BusinessException(ErrorCode.AI_EXECUTION_FAILED);
    }

    private static UUID stableId(String namespace, UUID value) {
        return UUID.nameUUIDFromBytes(
                (namespace + value).getBytes(StandardCharsets.UTF_8));
    }
}
