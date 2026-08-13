package com.insurance.platform.chat.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
    @Select("""
            SELECT * FROM iap_chat_message
            WHERE chat_request_id = #{requestId}
            ORDER BY sequence_no ASC
            """)
    List<ChatMessageEntity> findByRequest(@Param("requestId") long requestId);

    @Select("""
            SELECT COALESCE(MAX(sequence_no), 0) FROM iap_chat_message
            WHERE conversation_id = #{conversationId}
            """)
    long maxSequence(@Param("conversationId") long conversationId);

    @Select("""
            SELECT * FROM iap_chat_message
            WHERE conversation_id = #{conversationId}
            ORDER BY sequence_no ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ChatMessageEntity> findPage(
            @Param("conversationId") long conversationId,
            @Param("offset") long offset,
            @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM iap_chat_message WHERE conversation_id = #{conversationId}")
    long countByConversation(@Param("conversationId") long conversationId);
}
