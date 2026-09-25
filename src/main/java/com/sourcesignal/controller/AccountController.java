package com.sourcesignal.controller;

import com.sourcesignal.common.Result;
import com.sourcesignal.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 账号设置控制器
 */
@Tag(name = "账号设置", description = "个人资料、订阅配置、关键词过滤、数据隐私")
@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final UserService userService;

    @Operation(summary = "获取个人资料")
    @GetMapping("/profile")
    public Result<Map<String, Object>> getProfile() {
        return Result.success(userService.getProfile());
    }

    @Operation(summary = "更新个人资料")
    @PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body) {
        return Result.success(userService.updateProfile(body.get("company")));
    }

    @Operation(summary = "获取订阅配置")
    @GetMapping("/config")
    public Result<Map<String, Object>> getSubscriptionConfig() {
        return Result.success(userService.getSubscriptionConfig());
    }

    @Operation(summary = "更新监控品类")
    @PutMapping("/config/categories")
    public Result<Map<String, Object>> updateCategories(@RequestBody List<String> categories) {
        return Result.success(userService.updateCategories(categories));
    }

    @Operation(summary = "更新目标地区")
    @PutMapping("/config/regions")
    public Result<Map<String, Object>> updateRegions(@RequestBody List<String> regions) {
        return Result.success(userService.updateRegions(regions));
    }

    @Operation(summary = "更新自定义关键词（付费版）")
    @PutMapping("/config/keywords")
    public Result<Map<String, Object>> updateKeywords(@RequestBody List<String> keywords) {
        return Result.success(userService.updateKeywords(keywords));
    }

    @Operation(summary = "获取数据与隐私信息")
    @GetMapping("/privacy")
    public Result<Map<String, Object>> getDataPrivacy() {
        return Result.success(userService.getDataPrivacy());
    }
}
