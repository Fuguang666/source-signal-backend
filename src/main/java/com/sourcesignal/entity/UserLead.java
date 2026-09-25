package com.sourcesignal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户-线索关联实体
 * 记录用户对线索的标记、备注等个性化操作
 */
@Entity
@Table(name = "user_lead", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_lead", columnNames = {"user_id", "lead_id"})
}, indexes = {
        @Index(name = "idx_user_lead_user", columnList = "user_id"),
        @Index(name = "idx_user_lead_marked", columnList = "marked")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "lead_id", nullable = false)
    private Long leadId;

    /** 是否标记为跟进中 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean marked = false;

    /** 跟进备注 */
    @Column(columnDefinition = "TEXT")
    private String note;

    /** 是否已读 */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean read = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
