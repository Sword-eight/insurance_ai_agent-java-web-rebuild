package com.insurance.platform.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("""
            SELECT id FROM iap_user
            WHERE user_id = #{userId} AND status = 'ACTIVE' AND deleted_at IS NULL
            """)
    Long findActiveInternalId(@Param("userId") String userId);
}
