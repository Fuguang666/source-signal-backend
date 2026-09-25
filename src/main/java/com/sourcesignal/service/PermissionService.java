package com.sourcesignal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sourcesignal.common.BusinessException;
import com.sourcesignal.common.ResultCode;
import com.sourcesignal.entity.Subscription;
import com.sourcesignal.entity.UserSubscriptionConfig;
import com.sourcesignal.enums.PlanType;
import com.sourcesignal.enums.SubscriptionStatus;
import com.sourcesignal.mapper.SubscriptionMapper;
import com.sourcesignal.mapper.UserSubscriptionConfigMapper;
import com.sourcesignal.security.CurrentUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 权限校验服务
 * 负责试用版/付费版权限矩阵的统一校验
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final SubscriptionMapper subscriptionMapper;
    private final UserSubscriptionConfigMapper configMapper;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    @Value("${app.trial.max-categories:2}")
    private int trialMaxCategories;

    @Value("${app.trial.max-regions:1}")
    private int trialMaxRegions;

    @Value("${app.trial.history-days:7}")
    private int trialHistoryDays;

    @Value("${app.paid.history-days:30}")
    private int paidHistoryDays;

    @Value("${app.trial.daily-leads:3}")
    private int trialDailyLeads;

    /**
     * 获取当前用户订阅
     */
    public Subscription getCurrentSubscription() {
        Long userId = currentUser.getCurrentUserId();
        Subscription subscription = subscriptionMapper.selectOne(new LambdaQueryWrapper<Subscription>()
                .eq(Subscription::getUserId, userId));
        if (subscription == null) {
            throw new BusinessException(ResultCode.SUBSCRIPTION_NOT_FOUND);
        }
        return subscription;
    }

    /**
     * 是否为付费版用户
     */
    public boolean isPaidUser() {
        try {
            Subscription sub = getCurrentSubscription();
            return sub.getPlanType() == PlanType.PAID
                    && sub.getStatus() == SubscriptionStatus.ACTIVE;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 校验是否为付费版，否则抛出异常
     */
    public void requirePaid(String featureName) {
        if (!isPaidUser()) {
            throw new BusinessException(403, featureName + " 需升级付费版解锁");
        }
    }

    /**
     * 校验订阅是否有效（试用中或已付费）
     */
    public void requireActiveSubscription() {
        Subscription sub = getCurrentSubscription();
        if (sub.getStatus() == SubscriptionStatus.EXPIRED) {
            throw new BusinessException(ResultCode.SUBSCRIPTION_EXPIRED);
        }
        // 检查试用是否到期
        if (sub.getStatus() == SubscriptionStatus.TRIAL && sub.getTrialEndDate() != null) {
            if (LocalDate.now().isAfter(sub.getTrialEndDate())) {
                throw new BusinessException(ResultCode.SUBSCRIPTION_EXPIRED);
            }
        }
    }

    /**
     * 获取剩余试用天数
     */
    public long getTrialDaysLeft() {
        Subscription sub = getCurrentSubscription();
        if (sub.getStatus() != SubscriptionStatus.TRIAL || sub.getTrialEndDate() == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), sub.getTrialEndDate());
        return Math.max(0, days);
    }

    /**
     * 获取历史线索可见天数
     */
    public int getHistoryDays() {
        return isPaidUser() ? paidHistoryDays : trialHistoryDays;
    }

    /**
     * 获取历史线索可见起始时间
     */
    public LocalDateTime getHistorySince() {
        return LocalDateTime.now().minusDays(getHistoryDays());
    }

    /**
     * 校验监控品类数量是否超限
     */
    public void checkCategoryLimit(List<String> targetCategories) {
        if (isPaidUser()) return;
        if (targetCategories.size() > trialMaxCategories) {
            throw new BusinessException(403,
                    "试用版最多监控 " + trialMaxCategories + " 个品类，升级付费版解锁全品类");
        }
    }

    /**
     * 校验目标地区数量是否超限
     */
    public void checkRegionLimit(List<String> targetRegions) {
        if (isPaidUser()) return;
        if (targetRegions.size() > trialMaxRegions) {
            throw new BusinessException(403,
                    "试用版最多监控 " + trialMaxRegions + " 个地区，升级付费版解锁全地区");
        }
    }

    /**
     * 校验今日样例线索是否达上限（试用版）
     */
    public void checkDailySampleLimit() {
        if (isPaidUser()) return;
        Subscription sub = getCurrentSubscription();
        int used = sub.getTodaySampleCount() != null ? sub.getTodaySampleCount() : 0;
        if (used >= trialDailyLeads) {
            throw new BusinessException(ResultCode.TRIAL_LIMIT_REACHED);
        }
    }

    /**
     * 递增今日样例线索计数
     */
    public void incrementDailySampleCount() {
        if (isPaidUser()) return;
        Subscription sub = getCurrentSubscription();
        // 如果是新的一天，重置计数
        if (sub.getSampleResetAt() == null
                || sub.getSampleResetAt().toLocalDate().isBefore(LocalDate.now())) {
            sub.setTodaySampleCount(1);
            sub.setSampleResetAt(LocalDateTime.now());
        } else {
            sub.setTodaySampleCount(sub.getTodaySampleCount() + 1);
        }
        subscriptionMapper.updateById(sub);
    }

    /**
     * 获取当前用户监控品类
     */
    public List<String> getCurrentCategories() {
        Long userId = currentUser.getCurrentUserId();
        UserSubscriptionConfig config = configMapper.selectOne(new LambdaQueryWrapper<UserSubscriptionConfig>()
                .eq(UserSubscriptionConfig::getUserId, userId));
        if (config == null) return new ArrayList<>();
        try {
            return objectMapper.readValue(config.getCategories(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 获取当前用户监控地区
     */
    public List<String> getCurrentRegions() {
        Long userId = currentUser.getCurrentUserId();
        UserSubscriptionConfig config = configMapper.selectOne(new LambdaQueryWrapper<UserSubscriptionConfig>()
                .eq(UserSubscriptionConfig::getUserId, userId));
        if (config == null) return new ArrayList<>();
        try {
            return objectMapper.readValue(config.getRegions(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
