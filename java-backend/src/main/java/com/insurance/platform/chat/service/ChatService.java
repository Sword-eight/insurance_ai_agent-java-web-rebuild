package com.insurance.platform.chat.service;

import com.insurance.platform.chat.dto.ChatRequest;
import com.insurance.platform.chat.persistence.ChatPersistenceService;
import com.insurance.platform.chat.persistence.ChatRequestStatus;
import com.insurance.platform.chat.persistence.PreparedChat;
import com.insurance.platform.chat.idempotency.ChatIdempotencyCache;
import com.insurance.platform.chat.idempotency.IdempotencySummary;
import com.insurance.platform.chat.ratelimit.ChatRateLimiter;
import com.insurance.platform.chat.ratelimit.RateLimitDecision;
import com.insurance.platform.chat.vo.ChatResponse;
import com.insurance.platform.chat.vo.ChatSource;
import com.insurance.platform.client.AgentClient;
import com.insurance.platform.client.dto.AgentChatRequest;
import com.insurance.platform.client.dto.AgentChatResponse;
import com.insurance.platform.client.exception.AgentClientException;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import com.insurance.platform.common.exception.RateLimitExceededException;
import com.insurance.platform.security.CurrentUserProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Orchestrates two short database transactions around one transaction-free AI call. */
@Service
public class ChatService {
    private final AgentClient agentClient;
    private final ChatPersistenceService persistenceService;
    private final ChatRateLimiter rateLimiter;
    private final ChatIdempotencyCache idempotencyCache;
    private final ChatHistoryService historyService;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public ChatService(
            AgentClient agentClient,
            ChatPersistenceService persistenceService,
            ChatRateLimiter rateLimiter,
            ChatIdempotencyCache idempotencyCache,
            ChatHistoryService historyService,
            CurrentUserProvider currentUserProvider,
            Clock clock) {
        this.agentClient = agentClient;
        this.persistenceService = persistenceService;
        this.rateLimiter = rateLimiter;
        this.idempotencyCache = idempotencyCache;
        this.historyService = historyService;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    public ChatResponse chat(ChatRequest request, UUID idempotencyKey, String traceId) {
        UUID userId = currentUserProvider.requireUserId();
        RateLimitDecision decision = rateLimiter.acquire(userId);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
        String requestHash = requestHash(request.conversationId(), request.message());
        UUID cachedRequestId = idempotencyCache.find(userId, idempotencyKey)
                .filter(summary -> requestHash.equals(summary.requestHash()))
                .map(IdempotencySummary::requestId)
                .orElse(null);
        PreparedChat prepared;
        try {
            prepared = persistenceService.prepare(
                    request.conversationId(), request.message(), idempotencyKey,
                    requestHash, cachedRequestId);
        } catch (DuplicateKeyException exception) {
            prepared = persistenceService.replayAfterDuplicate(
                    request.conversationId(), idempotencyKey, requestHash);
        }
        updateIdempotency(userId, idempotencyKey, prepared, requestHash, prepared.status());
        if (prepared.isReplay()) {
            return prepared.replayResponse();
        }
        historyService.evict(prepared.conversationId());

        AgentChatResponse internalResponse;
        try {
            var history = historyService.load(
                    userId,
                    prepared.conversationId(),
                    prepared.internalConversationId(),
                    prepared.internalRequestId());
            internalResponse = agentClient.chat(
                    new AgentChatRequest(
                            prepared.requestId(),
                            prepared.conversationId(),
                            request.message(),
                            history),
                    traceId);
        } catch (AgentClientException exception) {
            BusinessException publicFailure = mapClientFailure(exception);
            ChatRequestStatus terminalStatus = isDeliveryUnknown(exception)
                    ? ChatRequestStatus.UNKNOWN : ChatRequestStatus.FAILED;
            persistenceService.completeFailure(
                    prepared, terminalStatus, publicFailure.errorCode());
            afterTerminalCommit(
                    userId, idempotencyKey, prepared, requestHash, terminalStatus);
            throw publicFailure;
        } catch (RuntimeException exception) {
            persistenceService.completeFailure(
                    prepared, ChatRequestStatus.FAILED, ErrorCode.INTERNAL_ERROR);
            afterTerminalCommit(
                    userId, idempotencyKey, prepared, requestHash, ChatRequestStatus.FAILED);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        try {
            String answer = validateAnswer(internalResponse, prepared.requestId());
            List<ChatSource> sources = mapSources(internalResponse.sources());
            ChatResponse response = persistenceService.completeSuccess(prepared, answer, sources);
            afterTerminalCommit(
                    userId, idempotencyKey, prepared, requestHash, ChatRequestStatus.SUCCEEDED);
            return response;
        } catch (BusinessException exception) {
            persistenceService.completeFailure(
                    prepared, ChatRequestStatus.FAILED, exception.errorCode());
            afterTerminalCommit(
                    userId, idempotencyKey, prepared, requestHash, ChatRequestStatus.FAILED);
            throw exception;
        }
    }

    private void afterTerminalCommit(
            UUID userId,
            UUID idempotencyKey,
            PreparedChat prepared,
            String requestHash,
            ChatRequestStatus status) {
        updateIdempotency(userId, idempotencyKey, prepared, requestHash, status);
        historyService.evict(prepared.conversationId());
    }

    private void updateIdempotency(
            UUID userId,
            UUID idempotencyKey,
            PreparedChat prepared,
            String requestHash,
            ChatRequestStatus status) {
        idempotencyCache.put(
                userId,
                idempotencyKey,
                new IdempotencySummary(
                        prepared.requestId(), requestHash, status.name(), clock.instant()));
    }

    private static String validateAnswer(AgentChatResponse response, UUID requestId) {
        if (response == null
                || !requestId.equals(response.requestId())
                || response.answer() == null
                || response.answer().isBlank()
                || response.durationMs() < 0) {
            throw new BusinessException(ErrorCode.AI_EXECUTION_FAILED);
        }
        String answer = response.answer().trim();
        if (answer.length() > 4000) {
            throw new BusinessException(ErrorCode.AI_EXECUTION_FAILED);
        }
        return answer;
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
        return new ChatSource(
                documentName,
                optionalInteger(source.get("page")),
                snippet,
                optionalDouble(source.get("score")));
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
            case UNAVAILABLE, DELIVERY_UNKNOWN ->
                    new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            case PROTOCOL -> new BusinessException(ErrorCode.AI_EXECUTION_FAILED);
            case REJECTED -> mapRejectedCode(exception.internalCode());
        };
    }

    private static boolean isDeliveryUnknown(AgentClientException exception) {
        return exception.kind() == AgentClientException.Kind.TIMEOUT
                || exception.kind() == AgentClientException.Kind.DELIVERY_UNKNOWN
                || (exception.kind() == AgentClientException.Kind.REJECTED
                    && "AI_REQUEST_IN_PROGRESS".equals(exception.internalCode()));
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

    private static String requestHash(UUID conversationId, String message) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    (conversationId + "\n" + message).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
