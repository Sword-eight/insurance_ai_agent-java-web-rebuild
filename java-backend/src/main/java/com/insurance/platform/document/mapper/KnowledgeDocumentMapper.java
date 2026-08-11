package com.insurance.platform.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insurance.platform.document.entity.KnowledgeDocumentEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentEntity> {
    @Select("""
            SELECT * FROM iap_knowledge_document
            WHERE document_id = #{documentId} AND user_id = #{userId}
              AND deleted_at IS NULL
            """)
    KnowledgeDocumentEntity findOwned(
            @Param("documentId") String documentId, @Param("userId") long userId);

    @Select("""
            SELECT * FROM iap_knowledge_document
            WHERE user_id = #{userId} AND deleted_at IS NULL
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<KnowledgeDocumentEntity> findPage(
            @Param("userId") long userId,
            @Param("offset") long offset,
            @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM iap_knowledge_document
            WHERE user_id = #{userId} AND deleted_at IS NULL
            """)
    long countOwned(@Param("userId") long userId);

    @Update("""
            UPDATE iap_knowledge_document
            SET index_status = #{nextStatus}, error_code = #{errorCode},
                error_message = #{errorMessage}, updated_at = #{updatedAt}
            WHERE document_id = #{documentId} AND index_status = #{expectedStatus}
              AND deleted_at IS NULL
            """)
    int transition(
            @Param("documentId") String documentId,
            @Param("expectedStatus") String expectedStatus,
            @Param("nextStatus") String nextStatus,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("updatedAt") LocalDateTime updatedAt);
}
