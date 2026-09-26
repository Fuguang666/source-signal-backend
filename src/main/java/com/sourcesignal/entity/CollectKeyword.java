package com.sourcesignal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 采集关键词配置
 * 表名：collect_keyword
 * 后台可配置的 Reddit 搜索关键词，命中即进入 AI 打标队列
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("collect_keyword")
public class CollectKeyword {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 采集关键词 */
    @TableField("keyword")
    private String keyword;

    /** 关键词说明 */
    @TableField("note")
    private String note;

    /** 是否启用 */
    @TableField("enabled")
    @Builder.Default
    private Boolean enabled = true;

    /** 今日命中次数 */
    @TableField("today_hits")
    @Builder.Default
    private Integer todayHits = 0;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
