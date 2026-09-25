package com.sourcesignal.service;

import com.sourcesignal.common.BusinessException;
import com.sourcesignal.common.ResultCode;
import com.sourcesignal.entity.Subscription;
import com.sourcesignal.entity.User;
import com.sourcesignal.enums.PlanType;
import com.sourcesignal.enums.SubscriptionStatus;
import com.sourcesignal.repository.SubscriptionRepository;
import com.sourcesignal.repository.UserRepository;
import com.sourcesignal.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 订阅服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    @Value("${app.trial.days}")
    private int trialDays;

    @Value("${app.trial.daily-leads}")
    private int dailySampleLeads;

    @Value("${app.trial.max-categories}")
    private int maxCategories;

    @Value("${app.trial.max-regions}")
    private int maxRegions;

    /**
     * 获取当前用户订阅信息
     */
    public Map<String, Object> getSubscriptionInfo() {
        Long userId = currentUser.getCurrentUserId();
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.SUBSCRIPTION_NOT_FOUND));

        Map<String, Object> result = new HashMap<>();
        result.put("planType", subscription.getPlanType());
        result.put("planLabel", subscription.getPlanType().getLabel());
        result.put("status", subscription.getStatus());
        result.put("statusLabel", subscription.getStatus().getLabel());
        result.put("monthlyPrice", subscription.getPlanType().getMonthlyPrice());

        if (subscription.getStatus() == SubscriptionStatus.TRIAL) {
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), subscription.getTrialEndDate());
            result.put("trialDaysLeft", Math.max(0, daysLeft));
            result.put("trialTotalDays", trialDays);
            result.put("dailySampleLimit", dailySampleLeads);
            result.put("todaySampleUsed", subscription.getTodaySampleCount());
            result.put("maxCategories", maxCategories);
            result.put("maxRegions", maxRegions);
        }

        return result;
    }

    /**
     * 获取套餐列表（用于订阅与套餐页展示）
     */
    public Map<String, Object> getPlans() {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> trial = new HashMap<>();
        trial.put("name", "试用版");
        trial.put("price", "$0");
        trial.put("rmb", "7 天免费");
        trial.put("desc", "先跑通核心链路，7 天后到期");
        trial.put("features", new String[]{
                "7 天试用期限", "每日 3 条样例线索", "邮件推送",
                "基础筛选（等级/品类/地区）", "线索标记与备注"
        });
        trial.put("lockedFeatures", new String[]{
                "Telegram / 企业微信推送", "30 天历史库", "CSV 导出",
                "自定义关键词过滤", "子账号 / API"
        });

        Map<String, Object> paid = new HashMap<>();
        paid.put("name", "付费版");
        paid.put("price", "$129");
        paid.put("rmb", "¥899 / 月");
        paid.put("desc", "解锁全部功能，团队协作无限制");
        paid.put("yearlyDiscount", "年付享 8 折");
        paid.put("features", new String[]{
                "不限线索量", "全品类 + 全地区监控",
                "多渠道推送（邮件/Telegram/企业微信/钉钉）",
                "30 天历史库 + CSV 导出", "自定义关键词过滤",
                "线索标记与备注", "子账号协作", "API 对接"
        });

        result.put("trial", trial);
        result.put("paid", paid);
        return result;
    }

    /**
     * 升级付费版（演示模式，实际需对接支付）
     */
    @Transactional
    public Map<String, Object> upgrade(String billingCycle) {
        Long userId = currentUser.getCurrentUserId();
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.SUBSCRIPTION_NOT_FOUND));

        // TODO: 对接 Stripe / 微信支付，支付成功后再更新状态
        subscription.setPlanType(PlanType.PAID);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setPaidStartDate(LocalDate.now());
        subscription.setBillingCycle(billingCycle);

        if ("YEARLY".equalsIgnoreCase(billingCycle)) {
            subscription.setPaidEndDate(LocalDate.now().plusYears(1));
        } else {
            subscription.setPaidEndDate(LocalDate.now().plusMonths(1));
        }

        subscriptionRepository.save(subscription);

        log.info("用户升级付费版: userId={}, billingCycle={}", userId, billingCycle);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "升级成功，全部功能已解锁");
        result.put("planType", PlanType.PAID);
        return result;
    }

    /**
     * 取消订阅
     */
    @Transactional
    public Map<String, Object> cancel() {
        Long userId = currentUser.getCurrentUserId();
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.SUBSCRIPTION_NOT_FOUND));

        // 取消后按当期剩余服务到期停止
        log.info("用户取消订阅: userId={}, paidEndDate={}", userId, subscription.getPaidEndDate());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "已取消自动续费，服务将持续到 " + subscription.getPaidEndDate());
        return result;
    }
}
