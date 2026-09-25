package com.sourcesignal.entity;

import com.sourcesignal.enums.PlanType;
import com.sourcesignal.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订阅实体
 * 一个用户对应一条订阅记录，状态随时间流转
 */
@Entity
@Table(name = "subscription", indexes = {
        @Index(name = "idx_sub_user", columnList = "user_id"),
        @Index(name = "idx_sub_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 套餐类型：TRIAL / PAID */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlanType planType;

    /** 订阅状态：TRIAL / ACTIVE / EXPIRED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionStatus status;

    /** 试用开始日期 */
    private LocalDate trialStartDate;

    /** 试用到期日期 */
    private LocalDate trialEndDate;

    /** 付费开始日期 */
    private LocalDate paidStartDate;

    /** 付费到期日期 */
    private LocalDate paidEndDate;

    /** 计费周期：MONTHLY / YEARLY */
    @Column(length = 16)
    private String billingCycle;

    /** 今日已推送样例线索数（试用版限流用） */
    @Column(nullable = false)
    @Builder.Default
    private Integer todaySampleCount = 0;

    /** 样例计数重置日期 */
    private LocalDateTime sampleResetAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
