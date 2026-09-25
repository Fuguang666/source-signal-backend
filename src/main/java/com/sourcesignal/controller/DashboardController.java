package com.sourcesignal.controller;

import com.sourcesignal.common.Result;
import com.sourcesignal.dto.DashboardDTO;
import com.sourcesignal.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘控制器
 */
@Tag(name = "仪表盘", description = "首页统计、监控状态、最近推送")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "获取仪表盘数据", description = "今日新信号、S级数量、试用状态、监控配置、品类分布、最近推送")
    @GetMapping
    public Result<DashboardDTO> getDashboard() {
        return Result.success(dashboardService.getDashboard());
    }
}
