package com.sourcesignal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 用户订阅配置（监控品类、目标地区、自定义关键词）
 * 表名：user_subscription_config
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_subscription_config")
public class UserSubscriptionConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 监控品类（JSON 数组，如 ["宠物用品","户外露营"]） */
    @TableField("categories")
    @Builder.Default
    private String categories = "[]";

    /** 目标地区（JSON 数组，如 ["北美"]） */
    @TableField("regions")
    @Builder.Default
    private String regions = "[]";

    /** 自定义关键词过滤（JSON 数组，付费版功能） */
    @TableField("keywords")
    @Builder.Default
    private String keywords = "[]";

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
