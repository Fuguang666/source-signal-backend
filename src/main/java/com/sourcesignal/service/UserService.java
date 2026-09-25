package com.sourcesignal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sourcesignal.entity.User;
import com.sourcesignal.entity.UserSubscriptionConfig;
import com.sourcesignal.mapper.UserMapper;
import com.sourcesignal.mapper.UserSubscriptionConfigMapper;
import com.sourcesignal.security.CurrentUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户服务 - 账号设置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserSubscriptionConfigMapper configMapper;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;
    private final PermissionService permissionService;

    /**
     * 获取个人资料
     */
    public Map<String, Object> getProfile() {
        User user = currentUser.getCurrentUser();
        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("email", user.getEmail());
        result.put("company", user.getCompany());
        result.put("lastLoginAt", user.getLastLoginAt());
        result.put("createdAt", user.getCreatedAt());
        return result;
    }

    /**
     * 更新个人资料
     */
    @Transactional
    public Map<String, Object> updateProfile(String company) {
        User user = currentUser.getCurrentUser();
        user.setCompany(company);
        userMapper.updateById(user);
        log.info("用户更新资料: userId={}", user.getId());
        return getProfile();
    }

    /**
     * 获取订阅配置（监控品类、地区、关键词）+ 权限信息
     */
    public Map<String, Object> getSubscriptionConfig() {
        Long userId = currentUser.getCurrentUserId();
        UserSubscriptionConfig config = configMapper.selectOne(new LambdaQueryWrapper<UserSubscriptionConfig>()
                .eq(UserSubscriptionConfig::getUserId, userId));
        if (config == null) {
            config = UserSubscriptionConfig.builder().userId(userId).build();
        }

        Map<String, Object> result = new HashMap<>();
        try {
            result.put("categories", objectMapper.readValue(config.getCategories(), new TypeReference<List<String>>() {}));
            result.put("regions", objectMapper.readValue(config.getRegions(), new TypeReference<List<String>>() {}));
            result.put("keywords", objectMapper.readValue(config.getKeywords(), new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            result.put("categories", new ArrayList<>());
            result.put("regions", new ArrayList<>());
            result.put("keywords", new ArrayList<>());
        }
        // 附加权限信息
        result.put("isPaid", permissionService.isPaidUser());
        result.put("trialDaysLeft", permissionService.getTrialDaysLeft());
        result.put("maxCategories", permissionService.isPaidUser() ? Integer.MAX_VALUE : 2);
        result.put("maxRegions", permissionService.isPaidUser() ? Integer.MAX_VALUE : 1);
        return result;
    }

    /**
     * 更新监控品类（试用版限 2 个）
     */
    @Transactional
    public Map<String, Object> updateCategories(List<String> categories) {
        permissionService.checkCategoryLimit(categories);

        Long userId = currentUser.getCurrentUserId();
        UserSubscriptionConfig config = configMapper.selectOne(new LambdaQueryWrapper<UserSubscriptionConfig>()
                .eq(UserSubscriptionConfig::getUserId, userId));
        if (config == null) {
            config = UserSubscriptionConfig.builder().userId(userId).build();
            configMapper.insert(config);
        }
        try {
            config.setCategories(objectMapper.writeValueAsString(categories));
            configMapper.updateById(config);
            log.info("用户更新监控品类: userId={}, categories={}", userId, categories);
        } catch (Exception e) {
            log.error("更新监控品类失败", e);
        }
        return getSubscriptionConfig();
    }

    /**
     * 更新目标地区（试用版限 1 个）
     */
    @Transactional
    public Map<String, Object> updateRegions(List<String> regions) {
        permissionService.checkRegionLimit(regions);

        Long userId = currentUser.getCurrentUserId();
        UserSubscriptionConfig config = configMapper.selectOne(new LambdaQueryWrapper<UserSubscriptionConfig>()
                .eq(UserSubscriptionConfig::getUserId, userId));
        if (config == null) {
            config = UserSubscriptionConfig.builder().userId(userId).build();
            configMapper.insert(config);
        }
        try {
            config.setRegions(objectMapper.writeValueAsString(regions));
            configMapper.updateById(config);
            log.info("用户更新目标地区: userId={}, regions={}", userId, regions);
        } catch (Exception e) {
            log.error("更新目标地区失败", e);
        }
        return getSubscriptionConfig();
    }

    /**
     * 更新自定义关键词（付费版功能）
     */
    @Transactional
    public Map<String, Object> updateKeywords(List<String> keywords) {
        permissionService.requirePaid("自定义关键词过滤");

        Long userId = currentUser.getCurrentUserId();
        UserSubscriptionConfig config = configMapper.selectOne(new LambdaQueryWrapper<UserSubscriptionConfig>()
                .eq(UserSubscriptionConfig::getUserId, userId));
        if (config == null) {
            config = UserSubscriptionConfig.builder().userId(userId).build();
            configMapper.insert(config);
        }
        try {
            config.setKeywords(objectMapper.writeValueAsString(keywords));
            configMapper.updateById(config);
            log.info("用户更新关键词: userId={}", userId);
        } catch (Exception e) {
            log.error("更新关键词失败", e);
        }
        return getSubscriptionConfig();
    }

    /**
     * 获取数据与隐私信息
     */
    public Map<String, Object> getDataPrivacy() {
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", "Reddit 公开帖子 · 官方授权 API · 不转售个人隐私数据");
        result.put("historyDays", permissionService.isPaidUser() ? "30 天" : "7 天（试用版）");
        result.put("privacyPolicyUrl", "/privacy");
        return result;
    }
}
