package com.sourcesignal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Entity
@Table(name = "sys_user", indexes = {
        @Index(name = "idx_user_username", columnList = "username", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户名（登录账号） */
    @Column(nullable = false, length = 64, unique = true)
    private String username;

    /** 邮箱（可选，用于后续通知/找回密码） */
    @Column(length = 128)
    private String email;

    /** 密码（BCrypt 加密） */
    @Column(nullable = false, length = 128)
    private String password;

    /** 公司 / 团队名称 */
    @Column(length = 128)
    private String company;

    /** 账号是否启用 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
