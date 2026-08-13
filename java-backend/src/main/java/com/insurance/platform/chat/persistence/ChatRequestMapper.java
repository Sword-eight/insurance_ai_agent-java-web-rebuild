package com.insurance.platform.chat.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatRequestMapper extends BaseMapper<ChatRequestEntity> {
    @Select("""
            SELECT * FROM iap_chat_request
            WHERE user_id = #{userId} AND idempotency_key = #{key}
            """)
    ChatRequestEntity findByIdempotencyKey(
            @Param("userId") long userId, @Param("key") String key);

    @Select("""
            SELECT * FROM iap_chat_request
            WHERE user_id = #{userId} AND request_id = #{requestId}
            """)
    ChatRequestEntity findByRequestId(
            @Param("userId") long userId, @Param("requestId") String requestId);

    @Select("""
            SELECT * FROM iap_chat_request
            WHERE conversation_id = #{conversationId} AND status = 'SUCCEEDED'
              AND id <> #{excludeRequestId}
            ORDER BY completed_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<ChatRequestEntity> findRecentSucceeded(
            @Param("conversationId") long conversationId,
            @Param("excludeRequestId") long excludeRequestId,
            @Param("limit") int limit);
}
