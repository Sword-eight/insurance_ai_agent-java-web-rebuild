package com.insurance.platform.conversation.service;

import com.insurance.platform.common.api.PageResponse;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import com.insurance.platform.conversation.dto.CreateConversationRequest;
import com.insurance.platform.conversation.entity.ConversationEntity;
import com.insurance.platform.conversation.mapper.ConversationMapper;
import com.insurance.platform.conversation.vo.ConversationView;
import com.insurance.platform.security.CurrentUserProvider;
import com.insurance.platform.user.mapper.UserMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {
    private final CurrentUserProvider currentUserProvider;
    private final UserMapper userMapper;
    private final ConversationMapper conversationMapper;

    public ConversationService(
            CurrentUserProvider currentUserProvider,
            UserMapper userMapper,
            ConversationMapper conversationMapper) {
        this.currentUserProvider = currentUserProvider;
        this.userMapper = userMapper;
        this.conversationMapper = conversationMapper;
    }

    @Transactional
    public ConversationView create(CreateConversationRequest request) {
        long userId = requireInternalUserId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MILLIS);
        ConversationEntity entity = new ConversationEntity();
        entity.setConversationId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setTitle(request.title());
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        conversationMapper.insert(entity);
        return toView(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationView> list(int page, int size) {
        long userId = requireInternalUserId();
        long offset = (long) (page - 1) * size;
        return new PageResponse<>(
                conversationMapper.findPage(userId, offset, size).stream()
                        .map(ConversationService::toView).toList(),
                page,
                size,
                conversationMapper.countOwned(userId));
    }

    @Transactional(readOnly = true)
    public ConversationView get(UUID conversationId) {
        long userId = requireInternalUserId();
        ConversationEntity owned = conversationMapper.findOwned(conversationId.toString(), userId);
        if (owned != null) {
            return toView(owned);
        }
        if (conversationMapper.findExisting(conversationId.toString()) != null) {
            throw new BusinessException(ErrorCode.CONVERSATION_ACCESS_DENIED);
        }
        throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
    }

    private long requireInternalUserId() {
        UUID publicUserId = currentUserProvider.requireUserId();
        Long internalId = userMapper.findActiveInternalId(publicUserId.toString());
        if (internalId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        return internalId;
    }

    private static ConversationView toView(ConversationEntity entity) {
        return new ConversationView(
                UUID.fromString(entity.getConversationId()),
                entity.getTitle(),
                entity.getStatus(),
                entity.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
