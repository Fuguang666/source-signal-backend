package com.sourcesignal.entity;

import com.sourcesignal.enums.PushChannelType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 推送记录实体
 * 记录每条线索向每个用户、每个渠道的推送情况
 */
@Entity
@Table(name = "push_record", indexes = {
        @Index(name = "idx_push_user", columnList = "user_id"),
        @Index(name = "idx_push_lead", columnList = "lead_id"),
        @Index(name = "idx_push_channel", columnList = "channel"),
        @Index(name = "idx_push_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "lead_id", nullable = false)
    private Long leadId;

    /** 推送渠道 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PushChannelType channel;

    /** 推送状态：PENDING / SUCCESS / FAILED */
    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "PENDING";

    /** 失败原因 */
    @Column(length = 512)
    private String errorMessage;

    /** 推送耗时（毫秒） */
    private Long durationMs;

    /** 推送时间 */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime pushedAt;
}
