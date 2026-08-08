package com.insurance.platform.chat.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.insurance.platform.chat.vo.ChatResponse;
import com.insurance.platform.chat.vo.ChatSource;
import com.insurance.platform.client.dto.HistoryMessage;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import com.insurance.platform.conversation.entity.ConversationEntity;
import com.insurance.platform.conversation.mapper.ConversationMapper;
import com.insurance.platform.security.CurrentUserProvider;
import com.insurance.platform.user.mapper.UserMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatPersistenceService {
    private static final int HISTORY_PAIR_LIMIT = 5;

    private final CurrentUserProvider currentUserProvider;
    private final UserMapper userMapper;
    private final ConversationMapper conversationMapper;
    private final ChatRequestMapper requestMapper;
    private final ChatMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public ChatPersistenceService(
            CurrentUserProvider currentUserProvider,
            UserMapper userMapper,
            ConversationMapper conversationMapper,
            ChatRequestMapper requestMapper,
            ChatMessageMapper messageMapper,
            ObjectMapper objectMapper) {
        this.currentUserProvider = currentUserProvider;
        this.userMapper = userMapper;
        this.conversationMapper = conversationMapper;
        this.requestMapper = requestMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PreparedChat prepare(
            UUID conversationId,
            String message,
            UUID idempotencyKey,
            String requestHash) {
        long userId = requireInternalUserId();
        ChatRequestEntity existing = requestMapper.findByIdempotencyKey(
                userId, idempotencyKey.toString());
        if (existing != null) {
            return replay(existing, conversationId, requestHash);
        }

        ConversationEntity conversation = conversationMapper.lockActiveOwned(
                conversationId.toString(), userId);
        if (conversation == null) {
            rejectMissingOrForeignConversation(conversationId, userId);
        }

        LocalDateTime now = now();
        ChatRequestEntity request = new ChatRequestEntity();
        request.setRequestId(UUID.randomUUID().toString());
        request.setUserId(userId);
        request.setConversationId(conversation.getId());
        request.setIdempotencyKey(idempotencyKey.toString());
        request.setRequestHash(requestHash);
        request.setStatus(ChatRequestStatus.PROCESSING.name());
        request.setStartedAt(now);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        requestMapper.insert(request);

        ChatMessageEntity userMessage = new ChatMessageEntity();
        userMessage.setMessageId(UUID.randomUUID().toString());
        userMessage.setConversationId(conversation.getId());
        userMessage.setChatRequestId(request.getId());
        userMessage.setRole(ChatMessageRole.USER.name());
        userMessage.setContent(message);
        userMessage.setSequenceNo(messageMapper.maxSequence(conversation.getId()) + 1);
        userMessage.setCreatedAt(now);
        messageMapper.insert(userMessage);

        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);

        return new PreparedChat(
                request.getId(),
                conversation.getId(),
                conversationId,
                UUID.fromString(request.getRequestId()),
                UUID.fromString(userMessage.getMessageId()),
                loadHistory(conversation.getId(), request.getId()),
                null);
    }

    @Transactional(readOnly = true)
    public PreparedChat replayAfterDuplicate(
            UUID conversationId, UUID idempotencyKey, String requestHash) {
        long userId = requireInternalUserId();
        ChatRequestEntity existing = requestMapper.findByIdempotencyKey(
                userId, idempotencyKey.toString());
        if (existing == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return replay(existing, conversationId, requestHash);
    }

    @Transactional
    public ChatResponse completeSuccess(
            PreparedChat prepared,
            String answer,
            List<ChatSource> sources) {
        ConversationEntity conversation = conversationMapper.lockByInternalId(
                prepared.internalConversationId());
        ChatRequestEntity request = requestMapper.selectById(prepared.internalRequestId());
        if (conversation == null || request == null
                || !ChatRequestStatus.PROCESSING.name().equals(request.getStatus())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        LocalDateTime now = now();
        ChatMessageEntity assistant = new ChatMessageEntity();
        assistant.setMessageId(UUID.randomUUID().toString());
        assistant.setConversationId(prepared.internalConversationId());
        assistant.setChatRequestId(prepared.internalRequestId());
        assistant.setRole(ChatMessageRole.ASSISTANT.name());
        assistant.setContent(answer);
        assistant.setSequenceNo(messageMapper.maxSequence(prepared.internalConversationId()) + 1);
        assistant.setCreatedAt(now);
        messageMapper.insert(assistant);

        request.setStatus(ChatRequestStatus.SUCCEEDED.name());
        request.setSourcesJson(writeSources(sources));
        request.setCompletedAt(now);
        request.setUpdatedAt(now);
        requestMapper.updateById(request);
        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);

        return new ChatResponse(
                prepared.conversationId(),
                prepared.requestId(),
                prepared.userMessageId(),
                UUID.fromString(assistant.getMessageId()),
                answer,
                sources);
    }

    @Transactional
    public void completeFailure(
            PreparedChat prepared, ChatRequestStatus status, ErrorCode errorCode) {
        if (status != ChatRequestStatus.FAILED && status != ChatRequestStatus.UNKNOWN) {
            throw new IllegalArgumentException("failure status must be terminal");
        }
        ChatRequestEntity request = requestMapper.selectById(prepared.internalRequestId());
        if (request == null || !ChatRequestStatus.PROCESSING.name().equals(request.getStatus())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        LocalDateTime now = now();
        request.setStatus(status.name());
        request.setErrorCode(errorCode.name());
        request.setErrorMessage(errorCode.defaultMessage());
        request.setCompletedAt(now);
        request.setUpdatedAt(now);
        requestMapper.updateById(request);
    }

    private PreparedChat replay(
            ChatRequestEntity existing,
            UUID conversationId,
            String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)
                || !conversationId.toString().equals(
                        conversationMapper.selectById(existing.getConversationId()).getConversationId())) {
            throw new BusinessException(ErrorCode.CHAT_IDEMPOTENCY_CONFLICT);
        }
        ChatRequestStatus status = ChatRequestStatus.valueOf(existing.getStatus());
        if (status == ChatRequestStatus.PROCESSING || status == ChatRequestStatus.RECEIVED) {
            throw new BusinessException(ErrorCode.CHAT_REQUEST_IN_PROGRESS);
        }
        if (status == ChatRequestStatus.FAILED || status == ChatRequestStatus.UNKNOWN) {
            throw new BusinessException(readErrorCode(existing.getErrorCode()));
        }
        List<ChatMessageEntity> messages = messageMapper.findByRequest(existing.getId());
        if (messages.size() != 2) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        ChatMessageEntity user = message(messages, ChatMessageRole.USER);
        ChatMessageEntity assistant = message(messages, ChatMessageRole.ASSISTANT);
        ChatResponse response = new ChatResponse(
                conversationId,
                UUID.fromString(existing.getRequestId()),
                UUID.fromString(user.getMessageId()),
                UUID.fromString(assistant.getMessageId()),
                assistant.getContent(),
                readSources(existing.getSourcesJson()));
        return new PreparedChat(
                existing.getId(), existing.getConversationId(), conversationId,
                UUID.fromString(existing.getRequestId()),
                UUID.fromString(user.getMessageId()), List.of(), response);
    }

    private List<HistoryMessage> loadHistory(long conversationId, long currentRequestId) {
        List<ChatRequestEntity> recent = new ArrayList<>(requestMapper.findRecentSucceeded(
                conversationId, currentRequestId, HISTORY_PAIR_LIMIT));
        Collections.reverse(recent);
        List<HistoryMessage> history = new ArrayList<>();
        for (ChatRequestEntity request : recent) {
            for (ChatMessageEntity message : messageMapper.findByRequest(request.getId())) {
                history.add(new HistoryMessage(message.getRole().toLowerCase(), message.getContent()));
            }
        }
        return List.copyOf(history);
    }

    private void rejectMissingOrForeignConversation(UUID conversationId, long userId) {
        ConversationEntity owned = conversationMapper.findOwned(conversationId.toString(), userId);
        if (owned != null || conversationMapper.findExisting(conversationId.toString()) == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        throw new BusinessException(ErrorCode.CONVERSATION_ACCESS_DENIED);
    }

    private long requireInternalUserId() {
        Long internalId = userMapper.findActiveInternalId(
                currentUserProvider.requireUserId().toString());
        if (internalId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        return internalId;
    }

    private static ChatMessageEntity message(
            List<ChatMessageEntity> messages, ChatMessageRole role) {
        return messages.stream().filter(item -> role.name().equals(item.getRole()))
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
    }

    private String writeSources(List<ChatSource> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private List<ChatSource> readSources(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String source = node.isTextual() ? node.textValue() : json;
            return objectMapper.readValue(source, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static ErrorCode readErrorCode(String value) {
        try {
            return ErrorCode.valueOf(value);
        } catch (RuntimeException exception) {
            return ErrorCode.INTERNAL_ERROR;
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }
}
