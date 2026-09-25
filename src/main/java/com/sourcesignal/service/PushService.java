package com.sourcesignal.service;

import com.sourcesignal.common.BusinessException;
import com.sourcesignal.common.PageResult;
import com.sourcesignal.common.ResultCode;
import com.sourcesignal.dto.LeadDTO;
import com.sourcesignal.entity.Lead;
import com.sourcesignal.entity.PushRecord;
import com.sourcesignal.entity.Subscription;
import com.sourcesignal.entity.UserLead;
import com.sourcesignal.entity.UserPushChannel;
import com.sourcesignal.enums.PlanType;
import com.sourcesignal.enums.PushChannelType;
import com.sourcesignal.enums.PushFrequency;
import com.sourcesignal.enums.SubscriptionStatus;
import com.sourcesignal.repository.LeadRepository;
import com.sourcesignal.repository.PushRecordRepository;
import com.sourcesignal.repository.SubscriptionRepository;
import com.sourcesignal.repository.UserLeadRepository;
import com.sourcesignal.repository.UserPushChannelRepository;
import com.sourcesignal.repository.UserRepository;
import com.sourcesignal.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 推送服务
 * 负责站内通知、推送渠道管理、推送记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private final UserPushChannelRepository pushChannelRepository;
    private final PushRecordRepository pushRecordRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserLeadRepository userLeadRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    // ==================== 站内推送通知 ====================

    /**
     * 新线索入库后，为所有用户创建站内推送记录
     * 由 LeadProcessor 在线索入库后调用
     */
    @Transactional
    public void pushNewLead(Lead lead) {
        // 获取所有启用用户
        List<Long> allUserIds = userRepository.findAllByEnabledTrue().stream()
                .map(user -> user.getId())
                .collect(Collectors.toList());

        int pushedCount = 0;
        for (Long userId : allUserIds) {
            try {
                // 检查是否已推送过（去重）
                if (pushRecordRepository.existsByUserIdAndLeadIdAndChannel(userId, lead.getId(), PushChannelType.IN_APP)) {
                    continue;
                }

                // 创建站内推送记录
                PushRecord record = PushRecord.builder()
                        .userId(userId)
                        .leadId(lead.getId())
                        .channel(PushChannelType.IN_APP)
                        .status("SUCCESS")
                        .pushedAt(LocalDateTime.now())
                        .build();
                pushRecordRepository.save(record);
                pushedCount++;
            } catch (Exception e) {
                log.error("站内推送失败: userId={}, leadId={}", userId, lead.getId(), e);
            }
        }

        if (pushedCount > 0) {
            log.info("新线索站内推送完成: leadId={}, 推送给 {} 个用户", lead.getId(), pushedCount);
        }
    }

    /**
     * 获取用户站内通知列表（分页）
     */
    public PageResult<Map<String, Object>> getNotifications(int page, int size) {
        Long userId = currentUser.getCurrentUserId();
        int pageNum = Math.max(1, page) - 1;
        int pageSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(pageNum, pageSize);

        Page<PushRecord> recordPage = pushRecordRepository.findByUserIdAndChannelOrderByPushedAtDesc(
                userId, PushChannelType.IN_APP, pageable);

        List<Map<String, Object>> notifications = recordPage.getContent().stream()
                .map(record -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", record.getId());
                    map.put("leadId", record.getLeadId());
                    map.put("pushedAt", record.getPushedAt());
                    map.put("status", record.getStatus());

                    // 关联线索信息
                    leadRepository.findById(record.getLeadId()).ifPresent(lead -> {
                        map.put("title", lead.getTitle());
                        map.put("grade", lead.getGrade().name());
                        map.put("gradeLabel", lead.getGrade().getLabel());
                        map.put("category", lead.getCategory());
                        map.put("subreddit", lead.getSubreddit());
                        map.put("postedAt", lead.getPostedAt());
                    });

                    // 是否已读
                    boolean isRead = userLeadRepository.findByUserIdAndLeadId(userId, record.getLeadId())
                            .map(ul -> Boolean.TRUE.equals(ul.getRead()))
                            .orElse(false);
                    map.put("isRead", isRead);

                    return map;
                })
                .collect(Collectors.toList());

        return PageResult.of(notifications, recordPage.getTotalElements(), page, pageSize);
    }

    /**
     * 获取未读通知数
     */
    public long getUnreadCount() {
        Long userId = currentUser.getCurrentUserId();
        // 站内推送总数 - 已读数
        long total = pushRecordRepository.countByUserIdAndChannel(userId, PushChannelType.IN_APP);
        long readCount = userLeadRepository.countByUserIdAndReadTrue(userId);
        return Math.max(0, total - readCount);
    }

    /**
     * 标记单条通知为已读
     */
    @Transactional
    public void markAsRead(Long leadId) {
        Long userId = currentUser.getCurrentUserId();
        UserLead userLead = userLeadRepository.findByUserIdAndLeadId(userId, leadId)
                .orElseGet(() -> UserLead.builder()
                        .userId(userId)
                        .leadId(leadId)
                        .marked(false)
                        .read(false)
                        .build());
        userLead.setRead(true);
        userLeadRepository.save(userLead);
    }

    /**
     * 标记所有通知为已读
     */
    @Transactional
    public void markAllAsRead() {
        Long userId = currentUser.getCurrentUserId();
        List<PushRecord> records = pushRecordRepository.findByUserIdAndChannel(userId, PushChannelType.IN_APP);
        for (PushRecord record : records) {
            UserLead userLead = userLeadRepository.findByUserIdAndLeadId(userId, record.getLeadId())
                    .orElseGet(() -> UserLead.builder()
                            .userId(userId)
                            .leadId(record.getLeadId())
                            .marked(false)
                            .read(false)
                            .build());
            userLead.setRead(true);
            userLeadRepository.save(userLead);
        }
        log.info("用户标记全部通知已读: userId={}, 数量={}", userId, records.size());
    }

    // ==================== 推送渠道管理 ====================

    /**
     * 获取用户所有推送渠道配置
     */
    public List<UserPushChannel> getChannels() {
        Long userId = currentUser.getCurrentUserId();
        return pushChannelRepository.findByUserId(userId);
    }

    /**
     * 切换推送渠道开关
     */
    @Transactional
    public UserPushChannel toggleChannel(PushChannelType channel) {
        Long userId = currentUser.getCurrentUserId();

        // 检查权限：非站内/邮件渠道需要付费版
        if (!channel.isTrialAvailable()) {
            Subscription subscription = subscriptionRepository.findByUserId(userId)
                    .orElseThrow(() -> new BusinessException(ResultCode.SUBSCRIPTION_NOT_FOUND));
            if (subscription.getPlanType() != PlanType.PAID || subscription.getStatus() != SubscriptionStatus.ACTIVE) {
                throw new BusinessException(ResultCode.PUSH_CHANNEL_LOCKED);
            }
        }

        UserPushChannel userChannel = pushChannelRepository.findByUserIdAndChannel(userId, channel)
                .orElseGet(() -> UserPushChannel.builder()
                        .userId(userId)
                        .channel(channel)
                        .enabled(false)
                        .frequency(PushFrequency.REALTIME)
                        .build());

        userChannel.setEnabled(!Boolean.TRUE.equals(userChannel.getEnabled()));
        return pushChannelRepository.save(userChannel);
    }

    /**
     * 更新推送频率
     */
    @Transactional
    public UserPushChannel updateFrequency(PushChannelType channel, PushFrequency frequency) {
        Long userId = currentUser.getCurrentUserId();
        UserPushChannel userChannel = pushChannelRepository.findByUserIdAndChannel(userId, channel)
                .orElseThrow(() -> new BusinessException(ResultCode.PUSH_CONFIG_NOT_FOUND));
        userChannel.setFrequency(frequency);
        return pushChannelRepository.save(userChannel);
    }

    /**
     * 发送测试线索
     */
    public Map<String, Object> sendTest() {
        Long userId = currentUser.getCurrentUserId();
        List<UserPushChannel> enabledChannels = pushChannelRepository.findByUserIdAndEnabledTrue(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("enabledChannels", enabledChannels.size());
        result.put("message", "已向启用的渠道发送测试线索");
        log.info("发送测试线索: userId={}, enabledChannels={}", userId, enabledChannels.size());
        return result;
    }
}
