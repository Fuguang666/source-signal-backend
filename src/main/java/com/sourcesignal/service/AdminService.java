package com.sourcesignal.service;

import com.sourcesignal.common.PageResult;
import com.sourcesignal.entity.Lead;
import com.sourcesignal.entity.Subscription;
import com.sourcesignal.entity.User;
import com.sourcesignal.repository.LeadRepository;
import com.sourcesignal.repository.SubscriptionRepository;
import com.sourcesignal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 后台管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final LeadRepository leadRepository;

    /**
     * 分页查询用户列表
     */
    public PageResult<Map<String, Object>> listUsers(int page, int size) {
        Page<User> userPage = userRepository.findAll(PageRequest.of(Math.max(0, page - 1), Math.min(100, size)));
        var list = userPage.getContent().stream().map(user -> {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("id", user.getId());
            map.put("username", user.getUsername());
            map.put("email", user.getEmail());
            map.put("company", user.getCompany());
            map.put("enabled", user.getEnabled());
            map.put("lastLoginAt", user.getLastLoginAt());
            map.put("createdAt", user.getCreatedAt());
            subscriptionRepository.findByUserId(user.getId()).ifPresent(sub -> {
                map.put("planType", sub.getPlanType());
                map.put("status", sub.getStatus());
            });
            return map;
        }).toList();
        return PageResult.of(list, userPage.getTotalElements(), page, size);
    }

    /**
     * 更新用户订阅状态
     */
    @Transactional
    public Map<String, Object> updateSubscriptionStatus(Long userId, String status) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("订阅信息不存在"));
        subscription.setStatus(com.sourcesignal.enums.SubscriptionStatus.valueOf(status));
        subscriptionRepository.save(subscription);
        log.info("后台更新用户订阅状态: userId={}, status={}", userId, status);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("userId", userId);
        result.put("status", status);
        return result;
    }

    /**
     * 启用/禁用用户
     */
    @Transactional
    public Map<String, Object> toggleUserEnabled(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setEnabled(!Boolean.TRUE.equals(user.getEnabled()));
        userRepository.save(user);
        log.info("后台切换用户状态: userId={}, enabled={}", userId, user.getEnabled());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("userId", userId);
        result.put("enabled", user.getEnabled());
        return result;
    }

    /**
     * 分页查询未抽检线索
     */
    public PageResult<Lead> listUnreviewedLeads(int page, int size) {
        Page<Lead> leadPage = leadRepository.findByReviewedFalseOrderByPostedAtDesc(
                PageRequest.of(Math.max(0, page - 1), Math.min(100, size)));
        return PageResult.of(leadPage.getContent(), leadPage.getTotalElements(), page, size);
    }

    /**
     * 提交线索抽检结果
     */
    @Transactional
    public Map<String, Object> reviewLead(Long leadId, boolean accurate, String note) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new RuntimeException("线索不存在"));
        lead.setReviewed(true);
        lead.setReviewAccurate(accurate);
        lead.setReviewNote(note);
        leadRepository.save(lead);
        log.info("后台抽检线索: leadId={}, accurate={}", leadId, accurate);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("leadId", leadId);
        result.put("accurate", accurate);
        return result;
    }

    /**
     * 后台统计概览
     */
    public Map<String, Object> getStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalUsers", userRepository.count());
        result.put("totalLeads", leadRepository.count());
        result.put("unreviewedLeads", leadRepository.count() - 0); // TODO: 精确统计
        return result;
    }
}
