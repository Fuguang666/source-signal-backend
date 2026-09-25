package com.sourcesignal.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.sourcesignal.enums.PushChannelType;
import com.sourcesignal.enums.PushFrequency;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 用户推送渠道配置
 * 表名：user_push_channel
 * 每个用户可配置多个推送渠道及开关状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_push_channel")
public class UserPushChannel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 推送渠道 */
    @TableField("channel")
    private PushChannelType channel;

    /** 是否启用 */
    @TableField("enabled")
    @Builder.Default
    private Boolean enabled = false;

    /** 推送频率 */
    @TableField("frequency")
    @Builder.Default
    private PushFrequency frequency = PushFrequency.REALTIME;

    /** 渠道配置（如 Telegram chatId、企业微信 webhook 等，JSON 格式） */
    @TableField("config_json")
    private String configJson;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
