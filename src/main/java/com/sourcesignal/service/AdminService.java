package com.sourcesignal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sourcesignal.common.PageResult;
import com.sourcesignal.entity.Lead;
import com.sourcesignal.entity.Subscription;
import com.sourcesignal.entity.User;
import com.sourcesignal.mapper.LeadMapper;
import com.sourcesignal.mapper.SubscriptionMapper;
import com.sourcesignal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final UserMapper userMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final LeadMapper leadMapper;

    /**
     * 分页查询用户列表
     */
    public PageResult<Map<String, Object>> listUsers(int page, int size) {
        int pageNum = Math.max(1, page);
        int pageSize = Math.min(100, Math.max(1, size));
        Page<User> pageParam = new Page<>(pageNum, pageSize);
        IPage<User> userPage = userMapper.selectPage(pageParam, null);

        var list = userPage.getRecords().stream().map(user -> {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("id", user.getId());
            map.put("username", user.getUsername());
            map.put("email", user.getEmail());
            map.put("company", user.getCompany());
            map.put("enabled", user.getEnabled());
            map.put("lastLoginAt", user.getLastLoginAt());
            map.put("createdAt", user.getCreatedAt());
            Subscription sub = subscriptionMapper.selectOne(new LambdaQueryWrapper<Subscription>()
                    .eq(Subscription::getUserId, user.getId()));
            if (sub != null) {
                map.put("planType", sub.getPlanType());
                map.put("status", sub.getStatus());
            }
            return map;
        }).toList();
        return PageResult.of(list, userPage.getTotal(), page, size);
    }

    /**
     * 更新用户订阅状态
     */
    @Transactional
    public Map<String, Object> updateSubscriptionStatus(Long userId, String status) {
        Subscription subscription = subscriptionMapper.selectOne(new LambdaQueryWrapper<Subscription>()
                .eq(Subscription::getUserId, userId));
        if (subscription == null) {
            throw new RuntimeException("订阅信息不存在");
        }
        subscription.setStatus(com.sourcesignal.enums.SubscriptionStatus.valueOf(status));
        subscriptionMapper.updateById(subscription);
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
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setEnabled(!Boolean.TRUE.equals(user.getEnabled()));
        userMapper.updateById(user);
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
        int pageNum = Math.max(1, page);
        int pageSize = Math.min(100, Math.max(1, size));
        Page<Lead> pageParam = new Page<>(pageNum, pageSize);
        IPage<Lead> leadPage = leadMapper.selectPage(pageParam, new LambdaQueryWrapper<Lead>()
                .eq(Lead::getReviewed, false)
                .orderByDesc(Lead::getPostedAt));
        return PageResult.of(leadPage.getRecords(), leadPage.getTotal(), page, size);
    }

    /**
     * 提交线索抽检结果
     */
    @Transactional
    public Map<String, Object> reviewLead(Long leadId, boolean accurate, String note) {
        Lead lead = leadMapper.selectById(leadId);
        if (lead == null) {
            throw new RuntimeException("线索不存在");
        }
        lead.setReviewed(true);
        lead.setReviewAccurate(accurate);
        lead.setReviewNote(note);
        leadMapper.updateById(lead);
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
        result.put("totalUsers", userMapper.selectCount(null));
        result.put("totalLeads", leadMapper.selectCount(null));
        long unreviewed = leadMapper.selectCount(new LambdaQueryWrapper<Lead>()
                .eq(Lead::getReviewed, false));
        result.put("unreviewedLeads", unreviewed);
        return result;
    }
}
