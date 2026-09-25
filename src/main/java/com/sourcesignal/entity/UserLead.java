package com.sourcesignal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 用户-线索关联实体
 * 表名：user_lead
 * 记录用户对线索的标记、备注、已读等个性化操作
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_lead")
public class UserLead {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 线索ID */
    @TableField("lead_id")
    private Long leadId;

    /** 是否标记为跟进中 */
    @TableField("marked")
    @Builder.Default
    private Boolean marked = false;

    /** 跟进备注 */
    @TableField("note")
    private String note;

    /** 是否已读（列名 is_read，属性名 isRead 避免 MySQL 保留字冲突） */
    @TableField("is_read")
    @Builder.Default
    private Boolean isRead = false;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
