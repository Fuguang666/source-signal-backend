package com.sourcesignal.controller;

import com.sourcesignal.common.Result;
import com.sourcesignal.dto.AuthResponse;
import com.sourcesignal.dto.ChangePasswordRequest;
import com.sourcesignal.dto.LoginRequest;
import com.sourcesignal.dto.RefreshTokenRequest;
import com.sourcesignal.dto.RegisterRequest;
import com.sourcesignal.security.CurrentUser;
import com.sourcesignal.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器 - 注册/登录/刷新/登出/修改密码
 */
@Tag(name = "认证管理", description = "用户注册、登录、刷新令牌、登出、修改密码")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    @Operation(summary = "用户注册", description = "用户名密码注册，即创建 7 天试用账号，每日 3 条样例线索")
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @Operation(summary = "用户登录", description = "用户名密码登录，返回 JWT 访问令牌和刷新令牌")
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @Operation(summary = "刷新访问令牌", description = "使用刷新令牌获取新的访问令牌")
    @PostMapping("/refresh")
    public Result<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refreshToken(request.getRefreshToken()));
    }

    @Operation(summary = "用户登出", description = "将当前访问令牌加入黑名单，需在 Header 中携带 Bearer Token")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        authService.logout(token);
        return Result.success();
    }

    @Operation(summary = "修改密码", description = "验证原密码后设置新密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = currentUser.getCurrentUserId();
        authService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
        return Result.success();
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
