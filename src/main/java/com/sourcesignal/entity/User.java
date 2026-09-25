package com.sourcesignal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 用户实体
 * 表名：sys_user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名（登录账号，唯一） */
    @TableField("username")
    private String username;

    /** 密码（BCrypt 加密） */
    @TableField("password")
    private String password;

    /** 邮箱（可选） */
    @TableField("email")
    private String email;

    /** 公司 / 团队名称 */
    @TableField("company")
    private String company;

    /** 账号是否启用 */
    @TableField("enabled")
    @Builder.Default
    private Boolean enabled = true;

    /** 最后登录时间 */
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
