package com.sourcesignal.entity;

import com.sourcesignal.enums.LeadGrade;
import com.sourcesignal.enums.NeedType;
import com.sourcesignal.enums.OrderScale;
import com.sourcesignal.enums.Region;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 采购线索实体
 * 由 Reddit 采集 + AI 打标后入库，全局共享一份
 */
@Entity
@Table(name = "procurement_lead", indexes = {
        @Index(name = "idx_lead_grade", columnList = "grade"),
        @Index(name = "idx_lead_category", columnList = "category"),
        @Index(name = "idx_lead_region", columnList = "region"),
        @Index(name = "idx_lead_posted_at", columnList = "postedAt"),
        @Index(name = "idx_lead_external_id", columnList = "externalId", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reddit 帖子唯一 ID（去重用） */
    @Column(nullable = false, length = 64, unique = true)
    private String externalId;

    /** 来源 subreddit */
    @Column(nullable = false, length = 64)
    private String subreddit;

    /** 帖子标题 */
    @Column(nullable = false, length = 512)
    private String title;

    /** 帖子正文 */
    @Column(columnDefinition = "TEXT")
    private String body;

    /** 原文链接 */
    @Column(length = 512)
    private String sourceUrl;

    /** 作者 ID */
    @Column(length = 128)
    private String author;

    /** 作者 Karma（去噪用） */
    private Integer authorKarma;

    /** 意向等级 S/A/B */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private LeadGrade grade;

    /** 品类（如：宠物用品、户外露营、3C数码） */
    @Column(length = 64)
    private String category;

    /** 订单量级 */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private OrderScale orderScale;

    /** 需求类型 */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private NeedType needType;

    /** 目标地区 */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Region region;

    /** 帖子发布时间 */
    @Column(nullable = false)
    private LocalDateTime postedAt;

    /** 采集入库时间 */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime collectedAt;

    /** AI 打标完成时间 */
    private LocalDateTime taggedAt;

    /** 是否已人工抽检 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean reviewed = false;

    /** 抽检结果是否准确（后台管理用） */
    private Boolean reviewAccurate;

    /** 抽检备注 */
    @Column(length = 512)
    private String reviewNote;
}
