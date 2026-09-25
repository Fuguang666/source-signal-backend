package com.sourcesignal.scheduler;

import com.sourcesignal.entity.Subscription;
import com.sourcesignal.enums.SubscriptionStatus;
import com.sourcesignal.mapper.SubscriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 订阅状态定时检查
 * 1. 每天凌晨检查试用是否到期
 * 2. 每天凌晨重置每日样例计数
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private final SubscriptionMapper subscriptionMapper;

    /**
     * 每天凌晨 1 点执行：试用到期检查 + 每日计数重置
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void dailySubscriptionCheck() {
        log.info("开始每日订阅状态检查...");
        LocalDate today = LocalDate.now();
        int expiredCount = 0;
        int resetCount = 0;

        List<Subscription> allSubscriptions = subscriptionMapper.selectList(null);
        for (Subscription subscription : allSubscriptions) {
            boolean changed = false;

            // 1. 试用到期检查
            if (subscription.getStatus() == SubscriptionStatus.TRIAL
                    && subscription.getTrialEndDate() != null
                    && subscription.getTrialEndDate().isBefore(today)) {
                subscription.setStatus(SubscriptionStatus.EXPIRED);
                expiredCount++;
                changed = true;
                log.info("用户试用到期: userId={}, trialEndDate={}", subscription.getUserId(), subscription.getTrialEndDate());
            }

            // 2. 付费版到期检查
            if (subscription.getStatus() == SubscriptionStatus.ACTIVE
                    && subscription.getPaidEndDate() != null
                    && subscription.getPaidEndDate().isBefore(today)) {
                subscription.setStatus(SubscriptionStatus.EXPIRED);
                expiredCount++;
                changed = true;
                log.info("用户付费到期: userId={}, paidEndDate={}", subscription.getUserId(), subscription.getPaidEndDate());
            }

            // 3. 重置每日样例计数
            if (subscription.getTodaySampleCount() != null && subscription.getTodaySampleCount() > 0) {
                subscription.setTodaySampleCount(0);
                subscription.setSampleResetAt(java.time.LocalDateTime.now());
                resetCount++;
                changed = true;
            }

            if (changed) {
                subscriptionMapper.updateById(subscription);
            }
        }

        log.info("每日订阅检查完成: 到期={}, 计数重置={}, 总数={}", expiredCount, resetCount, allSubscriptions.size());
    }
}
