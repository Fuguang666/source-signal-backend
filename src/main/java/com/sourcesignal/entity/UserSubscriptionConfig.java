package com.sourcesignal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户订阅配置（监控品类、目标地区、自定义关键词）
 */
@Entity
@Table(name = "user_subscription_config", indexes = {
        @Index(name = "idx_config_user", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscriptionConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** 监控品类（JSON 数组，如 ["宠物用品","户外露营"]） */
    @Column(length = 512)
    @Builder.Default
    private String categories = "[]";

    /** 目标地区（JSON 数组，如 ["北美"]） */
    @Column(length = 256)
    @Builder.Default
    private String regions = "[]";

    /** 自定义关键词过滤（JSON 数组，付费版功能） */
    @Column(length = 1024)
    @Builder.Default
    private String keywords = "[]";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
