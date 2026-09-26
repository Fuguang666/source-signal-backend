package com.sourcesignal.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sourcesignal.common.Result;
import com.sourcesignal.entity.CollectKeyword;
import com.sourcesignal.entity.CollectSubreddit;
import com.sourcesignal.mapper.CollectKeywordMapper;
import com.sourcesignal.mapper.CollectSubredditMapper;
import com.sourcesignal.service.CollectConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集配置管理控制器
 * 后台管理采集关键词和监控板块的增删改查、启用/停用
 */
@Tag(name = "采集配置管理", description = "采集关键词、监控板块的配置管理")
@RestController
@RequestMapping("/admin/collect-config")
@RequiredArgsConstructor
public class CollectConfigController {

    private final CollectKeywordMapper keywordMapper;
    private final CollectSubredditMapper subredditMapper;
    private final CollectConfigService collectConfigService;

    // ==================== 统计概览 ====================

    @Operation(summary = "采集器统计概览", description = "监控中板块数、已停用板块数、启用关键词数、今日已采集信号数")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getCollectStats() {
        long enabledSubs = subredditMapper.selectCount(
                new LambdaQueryWrapper<CollectSubreddit>().eq(CollectSubreddit::getEnabled, true)
        );
        long disabledSubs = subredditMapper.selectCount(
                new LambdaQueryWrapper<CollectSubreddit>().eq(CollectSubreddit::getEnabled, false)
        );
        long enabledKeywords = keywordMapper.selectCount(
                new LambdaQueryWrapper<CollectKeyword>().eq(CollectKeyword::getEnabled, true)
        );
        int todayTotal = subredditMapper.selectList(null).stream()
                .mapToInt(CollectSubreddit::getTodayNew)
                .sum();

        Map<String, Object> stats = new HashMap<>();
        stats.put("enabledSubreddits", enabledSubs);
        stats.put("disabledSubreddits", disabledSubs);
        stats.put("enabledKeywords", enabledKeywords);
        stats.put("todayCollected", todayTotal);
        return Result.success(stats);
    }

    // ==================== 监控板块管理 ====================

    @Operation(summary = "获取所有监控板块")
    @GetMapping("/subreddits")
    public Result<List<CollectSubreddit>> listSubreddits() {
        List<CollectSubreddit> list = subredditMapper.selectList(
                new LambdaQueryWrapper<CollectSubreddit>().orderByAsc(CollectSubreddit::getId)
        );
        return Result.success(list);
    }

    @Operation(summary = "新增监控板块")
    @PostMapping("/subreddits")
    public Result<CollectSubreddit> addSubreddit(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            return Result.error("板块名称不能为空");
        }
        // 去除 r/ 前缀
        name = name.trim().replaceAll("^r/", "");

        // 检查是否已存在
        Long exists = subredditMapper.selectCount(
                new LambdaQueryWrapper<CollectSubreddit>().eq(CollectSubreddit::getName, name)
        );
        if (exists != null && exists > 0) {
            return Result.error("板块 " + name + " 已存在");
        }

        CollectSubreddit sub = CollectSubreddit.builder()
                .name(name)
                .enabled(true)
                .todayNew(0)
                .build();
        subredditMapper.insert(sub);
        collectConfigService.refreshCache();
        return Result.success(sub);
    }

    @Operation(summary = "删除监控板块")
    @DeleteMapping("/subreddits/{id}")
    public Result<Void> deleteSubreddit(@PathVariable Long id) {
        subredditMapper.deleteById(id);
        collectConfigService.refreshCache();
        return Result.success(null);
    }

    @Operation(summary = "启用/停用监控板块")
    @PutMapping("/subreddits/{id}/toggle")
    public Result<CollectSubreddit> toggleSubreddit(@PathVariable Long id) {
        CollectSubreddit sub = subredditMapper.selectById(id);
        if (sub == null) {
            return Result.error("板块不存在");
        }
        sub.setEnabled(!sub.getEnabled());
        subredditMapper.updateById(sub);
        collectConfigService.refreshCache();
        return Result.success(sub);
    }

    // ==================== 采集关键词管理 ====================

    @Operation(summary = "获取所有采集关键词")
    @GetMapping("/keywords")
    public Result<List<CollectKeyword>> listKeywords() {
        List<CollectKeyword> list = keywordMapper.selectList(
                new LambdaQueryWrapper<CollectKeyword>().orderByAsc(CollectKeyword::getId)
        );
        return Result.success(list);
    }

    @Operation(summary = "新增采集关键词")
    @PostMapping("/keywords")
    public Result<CollectKeyword> addKeyword(@RequestBody Map<String, String> body) {
        String keyword = body.get("keyword");
        String note = body.get("note");
        if (keyword == null || keyword.trim().isEmpty()) {
            return Result.error("关键词不能为空");
        }
        keyword = keyword.trim();

        // 检查是否已存在
        Long exists = keywordMapper.selectCount(
                new LambdaQueryWrapper<CollectKeyword>().eq(CollectKeyword::getKeyword, keyword)
        );
        if (exists != null && exists > 0) {
            return Result.error("关键词 " + keyword + " 已存在");
        }

        CollectKeyword kw = CollectKeyword.builder()
                .keyword(keyword)
                .note(note != null ? note : "手动添加")
                .enabled(true)
                .todayHits(0)
                .build();
        keywordMapper.insert(kw);
        collectConfigService.refreshCache();
        return Result.success(kw);
    }

    @Operation(summary = "删除采集关键词")
    @DeleteMapping("/keywords/{id}")
    public Result<Void> deleteKeyword(@PathVariable Long id) {
        keywordMapper.deleteById(id);
        collectConfigService.refreshCache();
        return Result.success(null);
    }

    @Operation(summary = "启用/停用采集关键词")
    @PutMapping("/keywords/{id}/toggle")
    public Result<CollectKeyword> toggleKeyword(@PathVariable Long id) {
        CollectKeyword kw = keywordMapper.selectById(id);
        if (kw == null) {
            return Result.error("关键词不存在");
        }
        kw.setEnabled(!kw.getEnabled());
        keywordMapper.updateById(kw);
        collectConfigService.refreshCache();
        return Result.success(kw);
    }

    @Operation(summary = "刷新采集配置缓存", description = "手动清除缓存，使配置变更立即生效")
    @PostMapping("/refresh-cache")
    public Result<Void> refreshCache() {
        collectConfigService.refreshCache();
        return Result.success(null);
    }
}
