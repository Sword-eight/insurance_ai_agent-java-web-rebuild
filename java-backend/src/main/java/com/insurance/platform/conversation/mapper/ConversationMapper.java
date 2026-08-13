package com.insurance.platform.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insurance.platform.conversation.entity.ConversationEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConversationMapper extends BaseMapper<ConversationEntity> {

    @Select("""
            SELECT * FROM iap_conversation
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId} AND deleted_at IS NULL
            """)
    ConversationEntity findOwned(
            @Param("conversationId") String conversationId,
            @Param("userId") long userId);

    @Select("""
            SELECT * FROM iap_conversation
            WHERE conversation_id = #{conversationId} AND deleted_at IS NULL
            """)
    ConversationEntity findExisting(@Param("conversationId") String conversationId);

    @Select("""
            SELECT * FROM iap_conversation
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId} AND status = 'ACTIVE' AND deleted_at IS NULL
            FOR UPDATE
            """)
    ConversationEntity lockActiveOwned(
            @Param("conversationId") String conversationId,
            @Param("userId") long userId);

    @Select("SELECT * FROM iap_conversation WHERE id = #{id} FOR UPDATE")
    ConversationEntity lockByInternalId(@Param("id") long id);

    @Select("""
            SELECT * FROM iap_conversation
            WHERE user_id = #{userId} AND deleted_at IS NULL
            ORDER BY updated_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ConversationEntity> findPage(
            @Param("userId") long userId,
            @Param("offset") long offset,
            @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM iap_conversation
            WHERE user_id = #{userId} AND deleted_at IS NULL
            """)
    long countOwned(@Param("userId") long userId);
}
