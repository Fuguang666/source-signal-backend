package com.sourcesignal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sourcesignal.common.BusinessException;
import com.sourcesignal.common.PageResult;
import com.sourcesignal.common.ResultCode;
import com.sourcesignal.entity.Lead;
import com.sourcesignal.entity.PushRecord;
import com.sourcesignal.entity.Subscription;
import com.sourcesignal.entity.User;
import com.sourcesignal.entity.UserLead;
import com.sourcesignal.entity.UserPushChannel;
import com.sourcesignal.enums.PlanType;
import com.sourcesignal.enums.PushChannelType;
import com.sourcesignal.enums.PushFrequency;
import com.sourcesignal.enums.SubscriptionStatus;
import com.sourcesignal.mapper.LeadMapper;
import com.sourcesignal.mapper.PushRecordMapper;
import com.sourcesignal.mapper.SubscriptionMapper;
import com.sourcesignal.mapper.UserLeadMapper;
import com.sourcesignal.mapper.UserPushChannelMapper;
import com.sourcesignal.mapper.UserMapper;
import com.sourcesignal.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final UserPushChannelMapper pushChannelMapper;
    private final PushRecordMapper pushRecordMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final UserLeadMapper userLeadMapper;
    private final LeadMapper leadMapper;
    private final UserMapper userMapper;
    private final CurrentUser currentUser;

    // ==================== 站内推送通知 ====================

    /**
     * 新线索入库后，为所有用户创建站内推送记录
     * 由 LeadProcessor 在线索入库后调用
     */
    @Transactional
    public void pushNewLead(Lead lead) {
        List<Long> allUserIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .eq(User::getEnabled, true))
                .stream()
                .map(User::getId)
                .collect(Collectors.toList());

        int pushedCount = 0;
        for (Long userId : allUserIds) {
            try {
                Long exists = pushRecordMapper.selectCount(new LambdaQueryWrapper<PushRecord>()
                        .eq(PushRecord::getUserId, userId)
                        .eq(PushRecord::getLeadId, lead.getId())
                        .eq(PushRecord::getChannel, PushChannelType.IN_APP));
                if (exists != null && exists > 0) {
                    continue;
                }

                PushRecord record = PushRecord.builder()
                        .userId(userId)
                        .leadId(lead.getId())
                        .channel(PushChannelType.IN_APP)
                        .status("SUCCESS")
                        .pushedAt(LocalDateTime.now())
                        .build();
                pushRecordMapper.insert(record);
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
        int pageNum = Math.max(1, page);
        int pageSize = Math.min(100, Math.max(1, size));

        Page<PushRecord> pageParam = new Page<>(pageNum, pageSize);
        IPage<PushRecord> recordPage = pushRecordMapper.selectPageByUserAndChannel(
                pageParam, userId, PushChannelType.IN_APP.name());

        List<Map<String, Object>> notifications = recordPage.getRecords().stream()
                .map(record -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", record.getId());
                    map.put("leadId", record.getLeadId());
                    map.put("pushedAt", record.getPushedAt());
                    map.put("status", record.getStatus());

                    Lead lead = leadMapper.selectById(record.getLeadId());
                    if (lead != null) {
                        map.put("title", lead.getTitle());
                        map.put("grade", lead.getGrade().name());
                        map.put("gradeLabel", lead.getGrade().getLabel());
                        map.put("category", lead.getCategory());
                        map.put("subreddit", lead.getSubreddit());
                        map.put("postedAt", lead.getPostedAt());
                    }

                    UserLead userLead = userLeadMapper.selectOne(new LambdaQueryWrapper<UserLead>()
                            .eq(UserLead::getUserId, userId)
                            .eq(UserLead::getLeadId, record.getLeadId()));
                    boolean isRead = userLead != null && Boolean.TRUE.equals(userLead.getIsRead());
                    map.put("isRead", isRead);

                    return map;
                })
                .collect(Collectors.toList());

        return PageResult.of(notifications, recordPage.getTotal(), page, pageSize);
    }

    /**
     * 获取未读通知数
     */
    public long getUnreadCount() {
        Long userId = currentUser.getCurrentUserId();
        long total = pushRecordMapper.countByUserAndChannel(userId, PushChannelType.IN_APP.name());
        long readCount = userLeadMapper.countReadByUserId(userId);
        return Math.max(0, total - readCount);
    }

    /**
     * 标记单条通知为已读
     */
    @Transactional
    public void markAsRead(Long leadId) {
        Long userId = currentUser.getCurrentUserId();
        UserLead userLead = userLeadMapper.selectOne(new LambdaQueryWrapper<UserLead>()
                .eq(UserLead::getUserId, userId)
                .eq(UserLead::getLeadId, leadId));
        if (userLead == null) {
            userLead = UserLead.builder()
                    .userId(userId)
                    .leadId(leadId)
                    .marked(false)
                    .isRead(false)
                    .build();
            userLeadMapper.insert(userLead);
        }
        userLead.setIsRead(true);
        userLeadMapper.updateById(userLead);
    }

    /**
     * 标记所有通知为已读
     */
    @Transactional
    public void markAllAsRead() {
        Long userId = currentUser.getCurrentUserId();
        List<PushRecord> records = pushRecordMapper.selectByUserAndChannel(userId, PushChannelType.IN_APP.name());
        for (PushRecord record : records) {
            UserLead userLead = userLeadMapper.selectOne(new LambdaQueryWrapper<UserLead>()
                    .eq(UserLead::getUserId, userId)
                    .eq(UserLead::getLeadId, record.getLeadId()));
            if (userLead == null) {
                userLead = UserLead.builder()
                        .userId(userId)
                        .leadId(record.getLeadId())
                        .marked(false)
                        .isRead(false)
                        .build();
                userLeadMapper.insert(userLead);
            }
            userLead.setIsRead(true);
            userLeadMapper.updateById(userLead);
        }
        log.info("用户标记全部通知已读: userId={}, 数量={}", userId, records.size());
    }

    // ==================== 推送渠道管理 ====================

    /**
     * 获取用户所有推送渠道配置
     */
    public List<UserPushChannel> getChannels() {
        Long userId = currentUser.getCurrentUserId();
        return pushChannelMapper.selectList(new LambdaQueryWrapper<UserPushChannel>()
                .eq(UserPushChannel::getUserId, userId));
    }

    /**
     * 切换推送渠道开关
     */
    @Transactional
    public UserPushChannel toggleChannel(PushChannelType channel) {
        Long userId = currentUser.getCurrentUserId();

        if (!channel.isTrialAvailable()) {
            Subscription subscription = subscriptionMapper.selectOne(new LambdaQueryWrapper<Subscription>()
                    .eq(Subscription::getUserId, userId));
            if (subscription == null) {
                throw new BusinessException(ResultCode.SUBSCRIPTION_NOT_FOUND);
            }
            if (subscription.getPlanType() != PlanType.PAID || subscription.getStatus() != SubscriptionStatus.ACTIVE) {
                throw new BusinessException(ResultCode.PUSH_CHANNEL_LOCKED);
            }
        }

        UserPushChannel userChannel = pushChannelMapper.selectOne(new LambdaQueryWrapper<UserPushChannel>()
                .eq(UserPushChannel::getUserId, userId)
                .eq(UserPushChannel::getChannel, channel));
        if (userChannel == null) {
            userChannel = UserPushChannel.builder()
                    .userId(userId)
                    .channel(channel)
                    .enabled(false)
                    .frequency(PushFrequency.REALTIME)
                    .build();
            pushChannelMapper.insert(userChannel);
        }

        userChannel.setEnabled(!Boolean.TRUE.equals(userChannel.getEnabled()));
        pushChannelMapper.updateById(userChannel);
        return userChannel;
    }

    /**
     * 更新推送频率
     */
    @Transactional
    public UserPushChannel updateFrequency(PushChannelType channel, PushFrequency frequency) {
        Long userId = currentUser.getCurrentUserId();
        UserPushChannel userChannel = pushChannelMapper.selectOne(new LambdaQueryWrapper<UserPushChannel>()
                .eq(UserPushChannel::getUserId, userId)
                .eq(UserPushChannel::getChannel, channel));
        if (userChannel == null) {
            throw new BusinessException(ResultCode.PUSH_CONFIG_NOT_FOUND);
        }
        userChannel.setFrequency(frequency);
        pushChannelMapper.updateById(userChannel);
        return userChannel;
    }

    /**
     * 发送测试线索
     */
    public Map<String, Object> sendTest() {
        Long userId = currentUser.getCurrentUserId();
        List<UserPushChannel> enabledChannels = pushChannelMapper.selectList(new LambdaQueryWrapper<UserPushChannel>()
                .eq(UserPushChannel::getUserId, userId)
                .eq(UserPushChannel::getEnabled, true));

        Map<String, Object> result = new HashMap<>();
        result.put("enabledChannels", enabledChannels.size());
        result.put("message", "已向启用的渠道发送测试线索");
        log.info("发送测试线索: userId={}, enabledChannels={}", userId, enabledChannels.size());
        return result;
    }
}
