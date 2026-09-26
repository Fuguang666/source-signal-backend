package com.sourcesignal.controller;

import com.sourcesignal.common.PageResult;
import com.sourcesignal.common.Result;
import com.sourcesignal.entity.Lead;
import com.sourcesignal.scheduler.CollectionScheduler;
import com.sourcesignal.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台管理控制器
 */
@Tag(name = "后台管理", description = "用户管理、订阅管理、线索抽检")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final CollectionScheduler collectionScheduler;

    @Operation(summary = "后台统计概览")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.success(adminService.getStats());
    }

    @Operation(summary = "分页查询用户列表", description = "支持订阅状态筛选和邮箱/用户名搜索")
    @GetMapping("/users")
    public Result<PageResult<Map<String, Object>>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminService.listUsers(page, size, status, keyword));
    }

    @Operation(summary = "更新用户订阅状态")
    @PutMapping("/users/{userId}/subscription")
    public Result<Map<String, Object>> updateSubscriptionStatus(
            @PathVariable Long userId,
            @RequestParam String status) {
        return Result.success(adminService.updateSubscriptionStatus(userId, status));
    }

    @Operation(summary = "启用/禁用用户")
    @PostMapping("/users/{userId}/toggle")
    public Result<Map<String, Object>> toggleUserEnabled(@PathVariable Long userId) {
        return Result.success(adminService.toggleUserEnabled(userId));
    }

    @Operation(summary = "分页查询未抽检线索")
    @GetMapping("/leads/unreviewed")
    public Result<PageResult<Lead>> listUnreviewedLeads(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(adminService.listUnreviewedLeads(page, size));
    }

    @Operation(summary = "提交线索抽检结果")
    @PostMapping("/leads/{leadId}/review")
    public Result<Map<String, Object>> reviewLead(
            @PathVariable Long leadId,
            @RequestParam boolean accurate,
            @RequestParam(required = false) String note) {
        return Result.success(adminService.reviewLead(leadId, accurate, note));
    }

    @Operation(summary = "手动触发数据采集", description = "立即执行一次 Reddit 采集 + AI 打标 + 入库全流程")
    @PostMapping("/collection/trigger")
    public Result<CollectionScheduler.CollectionResult> triggerCollection() {
        return Result.success(collectionScheduler.triggerCollection("手动触发"));
    }

    @Operation(summary = "查询采集状态", description = "查看采集是否运行中、最后采集时间、入库数量")
    @GetMapping("/collection/status")
    public Result<CollectionScheduler.CollectionStatus> getCollectionStatus() {
        return Result.success(collectionScheduler.getStatus());
    }
}
