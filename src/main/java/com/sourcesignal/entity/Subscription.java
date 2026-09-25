package com.sourcesignal.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.sourcesignal.enums.PlanType;
import com.sourcesignal.enums.SubscriptionStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订阅实体
 * 表名：subscription
 * 一个用户对应一条订阅记录，状态随时间流转
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("subscription")
public class Subscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 套餐类型：TRIAL / PAID */
    @TableField("plan_type")
    @Builder.Default
    private PlanType planType = PlanType.TRIAL;

    /** 订阅状态：TRIAL / ACTIVE / EXPIRED */
    @TableField("status")
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.TRIAL;

    /** 试用开始日期 */
    @TableField("trial_start_date")
    private LocalDate trialStartDate;

    /** 试用到期日期 */
    @TableField("trial_end_date")
    private LocalDate trialEndDate;

    /** 付费开始日期 */
    @TableField("paid_start_date")
    private LocalDate paidStartDate;

    /** 付费到期日期 */
    @TableField("paid_end_date")
    private LocalDate paidEndDate;

    /** 计费周期：MONTHLY / YEARLY */
    @TableField("billing_cycle")
    private String billingCycle;

    /** 今日已查看样例线索数（试用版限流用） */
    @TableField("today_sample_count")
    @Builder.Default
    private Integer todaySampleCount = 0;

    /** 样例计数重置时间 */
    @TableField("sample_reset_at")
    private LocalDateTime sampleResetAt;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
