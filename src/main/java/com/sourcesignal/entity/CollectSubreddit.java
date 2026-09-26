package com.sourcesignal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 采集板块配置
 * 表名：collect_subreddit
 * 后台可配置的 Reddit 监控板块（subreddit），启用后实时采集新帖
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("collect_subreddit")
public class CollectSubreddit {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 板块名称（如 ChinaSourcing，不含 r/ 前缀） */
    @TableField("name")
    private String name;

    /** 是否启用采集 */
    @TableField("enabled")
    @Builder.Default
    private Boolean enabled = true;

    /** 今日新线索数 */
    @TableField("today_new")
    @Builder.Default
    private Integer todayNew = 0;

    /** 最近采集时间 */
    @TableField("last_collected_at")
    private LocalDateTime lastCollectedAt;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
