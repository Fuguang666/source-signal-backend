package com.sourcesignal.controller;

import com.sourcesignal.common.Result;
import com.sourcesignal.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 订阅与套餐控制器
 */
@Tag(name = "订阅与套餐", description = "订阅信息、套餐展示、升级、取消")
@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Operation(summary = "获取当前订阅信息")
    @GetMapping
    public Result<Map<String, Object>> getSubscriptionInfo() {
        return Result.success(subscriptionService.getSubscriptionInfo());
    }

    @Operation(summary = "获取套餐列表")
    @GetMapping("/plans")
    public Result<Map<String, Object>> getPlans() {
        return Result.success(subscriptionService.getPlans());
    }

    @Operation(summary = "升级付费版")
    @PostMapping("/upgrade")
    public Result<Map<String, Object>> upgrade(@RequestParam(defaultValue = "MONTHLY") String billingCycle) {
        return Result.success(subscriptionService.upgrade(billingCycle));
    }

    @Operation(summary = "取消订阅")
    @PostMapping("/cancel")
    public Result<Map<String, Object>> cancel() {
        return Result.success(subscriptionService.cancel());
    }
}
