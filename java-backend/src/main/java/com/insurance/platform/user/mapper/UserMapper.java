package com.insurance.platform.user.mapper;

import com.insurance.platform.user.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("""
            SELECT id FROM iap_user
            WHERE user_id = #{userId} AND status = 'ACTIVE' AND deleted_at IS NULL
            """)
    Long findActiveInternalId(@Param("userId") String userId);

    @Select("""
            SELECT id, user_id, username, password_hash, status,
                   created_at, updated_at, deleted_at
            FROM iap_user
            WHERE username = #{username}
            LIMIT 1
            """)
    UserEntity findByNormalizedUsername(@Param("username") String username);

    @Insert("""
            INSERT INTO iap_user
                (user_id, username, password_hash, status, created_at, updated_at, deleted_at)
            VALUES
                (#{userId}, #{username}, #{passwordHash}, #{status},
                 #{createdAt}, #{updatedAt}, #{deletedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(UserEntity user);
}
