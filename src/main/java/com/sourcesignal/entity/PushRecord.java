package com.sourcesignal.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.sourcesignal.enums.PushChannelType;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 推送记录实体
 * 表名：push_record
 * 记录每条线索向每个用户、每个渠道的推送情况
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("push_record")
public class PushRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 线索ID */
    @TableField("lead_id")
    private Long leadId;

    /** 推送渠道 */
    @TableField("channel")
    private PushChannelType channel;

    /** 推送状态：PENDING / SUCCESS / FAILED */
    @TableField("status")
    @Builder.Default
    private String status = "PENDING";

    /** 失败原因 */
    @TableField("error_message")
    private String errorMessage;

    /** 推送耗时（毫秒） */
    @TableField("duration_ms")
    private Long durationMs;

    /** 推送时间 */
    @TableField("pushed_at")
    private LocalDateTime pushedAt;
}
