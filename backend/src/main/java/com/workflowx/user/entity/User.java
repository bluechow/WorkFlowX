package com.workflowx.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，映射 V1 表 users（真实结构见 V1__identity_core.sql 与 docs/database/data-dictionary.md）。
 * 主键 AUTO_INCREMENT；created_at/updated_at 由数据库默认值维护，代码不赋值；
 * passwordHash 为 BCrypt 散列，禁止以任何形式出现在日志或对外模型中（Master Prompt §11/§29）。
 */
@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String email;

    private String passwordHash;

    private String nickname;

    private UserStatus status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
