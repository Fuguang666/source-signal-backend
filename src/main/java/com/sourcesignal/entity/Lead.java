package com.sourcesignal.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.sourcesignal.enums.LeadGrade;
import com.sourcesignal.enums.NeedType;
import com.sourcesignal.enums.OrderScale;
import com.sourcesignal.enums.Region;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 采购线索实体
 * 表名：procurement_lead（lead 是 MySQL 保留字）
 * 由 Reddit 采集 + AI 打标后入库，全局共享一份
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("procurement_lead")
public class Lead {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Reddit 帖子唯一 ID（去重用） */
    @TableField("external_id")
    private String externalId;

    /** 来源 subreddit */
    @TableField("subreddit")
    private String subreddit;

    /** 帖子标题 */
    @TableField("title")
    private String title;

    /** 帖子正文 */
    @TableField("body")
    private String body;

    /** 原文链接 */
    @TableField("source_url")
    private String sourceUrl;

    /** 作者 ID */
    @TableField("author")
    private String author;

    /** 作者 Karma（去噪用） */
    @TableField("author_karma")
    private Integer authorKarma;

    /** 意向等级 S/A/B */
    @TableField("grade")
    private LeadGrade grade;

    /** 品类（如：宠物用品、户外露营、3C数码） */
    @TableField("category")
    private String category;

    /** 订单量级 */
    @TableField("order_scale")
    private OrderScale orderScale;

    /** 需求类型 */
    @TableField("need_type")
    private NeedType needType;

    /** 目标地区 */
    @TableField("region")
    private Region region;

    /** 帖子发布时间 */
    @TableField("posted_at")
    private LocalDateTime postedAt;

    /** 采集入库时间 */
    @TableField("collected_at")
    private LocalDateTime collectedAt;

    /** AI 打标完成时间 */
    @TableField("tagged_at")
    private LocalDateTime taggedAt;

    /** 是否已人工抽检 */
    @TableField("reviewed")
    @Builder.Default
    private Boolean reviewed = false;

    /** 抽检结果是否准确（后台管理用） */
    @TableField("review_accurate")
    private Boolean reviewAccurate;

    /** 抽检备注 */
    @TableField("review_note")
    private String reviewNote;
}
