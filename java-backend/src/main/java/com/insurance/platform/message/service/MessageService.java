package com.insurance.platform.message.service;

import com.insurance.platform.chat.persistence.ChatMessageEntity;
import com.insurance.platform.chat.persistence.ChatMessageMapper;
import com.insurance.platform.chat.persistence.ChatRequestEntity;
import com.insurance.platform.chat.persistence.ChatRequestMapper;
import com.insurance.platform.common.api.PageResponse;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import com.insurance.platform.conversation.entity.ConversationEntity;
import com.insurance.platform.conversation.mapper.ConversationMapper;
import com.insurance.platform.message.vo.MessageView;
import com.insurance.platform.security.CurrentUserProvider;
import com.insurance.platform.user.mapper.UserMapper;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageService {
    private final CurrentUserProvider currentUserProvider;
    private final UserMapper userMapper;
    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatRequestMapper requestMapper;

    public MessageService(
            CurrentUserProvider currentUserProvider,
            UserMapper userMapper,
            ConversationMapper conversationMapper,
            ChatMessageMapper messageMapper,
            ChatRequestMapper requestMapper) {
        this.currentUserProvider = currentUserProvider;
        this.userMapper = userMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.requestMapper = requestMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageView> list(UUID conversationId, int page, int size) {
        Long userId = userMapper.findActiveInternalId(
                currentUserProvider.requireUserId().toString());
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        ConversationEntity conversation = conversationMapper.findOwned(
                conversationId.toString(), userId);
        if (conversation == null) {
            if (conversationMapper.findExisting(conversationId.toString()) != null) {
                throw new BusinessException(ErrorCode.CONVERSATION_ACCESS_DENIED);
            }
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        long offset = (long) (page - 1) * size;
        return new PageResponse<>(
                messageMapper.findPage(conversation.getId(), offset, size).stream()
                        .map(this::toView).toList(),
                page,
                size,
                messageMapper.countByConversation(conversation.getId()));
    }

    private MessageView toView(ChatMessageEntity message) {
        ChatRequestEntity request = requestMapper.selectById(message.getChatRequestId());
        if (request == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return new MessageView(
                UUID.fromString(message.getMessageId()),
                UUID.fromString(request.getRequestId()),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
