package com.sourcesignal.repository;

import com.sourcesignal.entity.PushRecord;
import com.sourcesignal.enums.PushChannelType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PushRecordRepository extends JpaRepository<PushRecord, Long> {
    List<PushRecord> findByUserIdOrderByPushedAtDesc(Long userId);

    long countByUserIdAndPushedAtAfter(Long userId, LocalDateTime since);

    boolean existsByUserIdAndLeadIdAndChannel(Long userId, Long leadId, PushChannelType channel);

    /** 按渠道分页查询用户推送记录 */
    Page<PushRecord> findByUserIdAndChannelOrderByPushedAtDesc(Long userId, PushChannelType channel, Pageable pageable);

    /** 查询用户指定渠道的所有推送记录 */
    List<PushRecord> findByUserIdAndChannel(Long userId, PushChannelType channel);

    /** 统计用户指定渠道的推送数 */
    long countByUserIdAndChannel(Long userId, PushChannelType channel);
}
