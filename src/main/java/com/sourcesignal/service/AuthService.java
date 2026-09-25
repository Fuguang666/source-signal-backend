package com.sourcesignal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sourcesignal.common.BusinessException;
import com.sourcesignal.common.ResultCode;
import com.sourcesignal.dto.AuthResponse;
import com.sourcesignal.dto.LoginRequest;
import com.sourcesignal.dto.RegisterRequest;
import com.sourcesignal.entity.Subscription;
import com.sourcesignal.entity.User;
import com.sourcesignal.entity.UserPushChannel;
import com.sourcesignal.entity.UserSubscriptionConfig;
import com.sourcesignal.enums.PlanType;
import com.sourcesignal.enums.PushChannelType;
import com.sourcesignal.enums.SubscriptionStatus;
import com.sourcesignal.mapper.SubscriptionMapper;
import com.sourcesignal.mapper.UserMapper;
import com.sourcesignal.mapper.UserPushChannelMapper;
import com.sourcesignal.mapper.UserSubscriptionConfigMapper;
import com.sourcesignal.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final UserSubscriptionConfigMapper configMapper;
    private final UserPushChannelMapper pushChannelMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${app.trial.days}")
    private int trialDays;

    /**
     * 用户注册（用户名密码）
     * 注册即创建 7 天试用账号，初始化订阅、配置
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 检查用户名是否已注册
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }

        // 创建用户
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .company(request.getCompany())
                .enabled(true)
                .build();
        userMapper.insert(user);

        // 创建试用订阅
        LocalDate now = LocalDate.now();
        Subscription subscription = Subscription.builder()
                .userId(user.getId())
                .planType(PlanType.TRIAL)
                .status(SubscriptionStatus.TRIAL)
                .trialStartDate(now)
                .trialEndDate(now.plusDays(trialDays))
                .todaySampleCount(0)
                .build();
        subscriptionMapper.insert(subscription);

        // 初始化订阅配置
        UserSubscriptionConfig config = UserSubscriptionConfig.builder()
                .userId(user.getId())
                .build();
        configMapper.insert(config);

        // 默认启用邮件推送渠道记录（实际推送功能暂不对接）
        UserPushChannel mailChannel = UserPushChannel.builder()
                .userId(user.getId())
                .channel(PushChannelType.EMAIL)
                .enabled(true)
                .build();
        pushChannelMapper.insert(mailChannel);

        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());

        return buildAuthResponse(user, subscription);
    }

    /**
     * 用户登录（用户名密码）
     */
    public AuthResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        Subscription subscription = subscriptionMapper.selectOne(new LambdaQueryWrapper<Subscription>()
                .eq(Subscription::getUserId, user.getId()));
        if (subscription == null) {
            throw new BusinessException(ResultCode.SUBSCRIPTION_NOT_FOUND);
        }

        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());

        return buildAuthResponse(user, subscription);
    }

    /**
     * 刷新访问令牌
     */
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(401, "刷新令牌无效或已过期");
        }
        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        Subscription subscription = subscriptionMapper.selectOne(new LambdaQueryWrapper<Subscription>()
                .eq(Subscription::getUserId, userId));
        if (subscription == null) {
            throw new BusinessException(ResultCode.SUBSCRIPTION_NOT_FOUND);
        }
        log.info("用户刷新令牌: userId={}", userId);
        return buildAuthResponse(user, subscription);
    }

    /**
     * 用户登出（将当前 token 加入黑名单）
     */
    public void logout(String token) {
        tokenBlacklistService.blacklist(token);
        log.info("用户登出成功");
    }

    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        log.info("用户修改密码成功: userId={}", userId);
    }

    private AuthResponse buildAuthResponse(User user, Subscription subscription) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .planType(subscription.getPlanType().name())
                .subscriptionStatus(subscription.getStatus().name())
                .build();
    }
}
