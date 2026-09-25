package com.sourcesignal.controller;

import com.sourcesignal.common.PageResult;
import com.sourcesignal.common.Result;
import com.sourcesignal.entity.UserPushChannel;
import com.sourcesignal.enums.PushChannelType;
import com.sourcesignal.enums.PushFrequency;
import com.sourcesignal.service.PushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 推送与通知控制器
 */
@Tag(name = "推送与通知", description = "站内通知、推送渠道管理、频率设置、测试推送")
@RestController
@RequestMapping("/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;

    // ==================== 站内通知 ====================

    @Operation(summary = "获取站内通知列表", description = "分页获取用户的站内推送通知")
    @GetMapping("/notifications")
    public Result<PageResult<Map<String, Object>>> getNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(pushService.getNotifications(page, size));
    }

    @Operation(summary = "获取未读通知数")
    @GetMapping("/notifications/unread-count")
    public Result<Long> getUnreadCount() {
        return Result.success(pushService.getUnreadCount());
    }

    @Operation(summary = "标记单条通知为已读")
    @PostMapping("/notifications/{leadId}/read")
    public Result<Void> markAsRead(@PathVariable Long leadId) {
        pushService.markAsRead(leadId);
        return Result.success();
    }

    @Operation(summary = "标记所有通知为已读")
    @PostMapping("/notifications/read-all")
    public Result<Void> markAllAsRead() {
        pushService.markAllAsRead();
        return Result.success();
    }

    // ==================== 推送渠道管理 ====================

    @Operation(summary = "获取推送渠道列表")
    @GetMapping("/channels")
    public Result<List<UserPushChannel>> getChannels() {
        return Result.success(pushService.getChannels());
    }

    @Operation(summary = "切换推送渠道开关")
    @PostMapping("/channels/{channel}/toggle")
    public Result<UserPushChannel> toggleChannel(@PathVariable PushChannelType channel) {
        return Result.success(pushService.toggleChannel(channel));
    }

    @Operation(summary = "更新推送频率")
    @PostMapping("/channels/{channel}/frequency")
    public Result<UserPushChannel> updateFrequency(@PathVariable PushChannelType channel,
                                                     @RequestParam PushFrequency frequency) {
        return Result.success(pushService.updateFrequency(channel, frequency));
    }

    @Operation(summary = "发送测试线索")
    @PostMapping("/test")
    public Result<Map<String, Object>> sendTest() {
        return Result.success(pushService.sendTest());
    }
}
