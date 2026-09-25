package com.sourcesignal.entity;

import com.sourcesignal.enums.PushChannelType;
import com.sourcesignal.enums.PushFrequency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户推送渠道配置
 * 每个用户可配置多个推送渠道及开关状态
 */
@Entity
@Table(name = "user_push_channel", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_channel", columnNames = {"user_id", "channel"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPushChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 推送渠道 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PushChannelType channel;

    /** 是否启用 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    /** 渠道配置（如 Telegram chatId、企业微信 webhook 等，JSON 格式） */
    @Column(length = 1024)
    private String configJson;

    /** 推送频率 */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    @Builder.Default
    private PushFrequency frequency = PushFrequency.REALTIME;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
