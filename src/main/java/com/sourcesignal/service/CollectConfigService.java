package com.sourcesignal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sourcesignal.entity.CollectKeyword;
import com.sourcesignal.entity.CollectSubreddit;
import com.sourcesignal.mapper.CollectKeywordMapper;
import com.sourcesignal.mapper.CollectSubredditMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 采集配置服务
 * 从数据库读取启用的关键词和板块列表，带内存缓存（30秒刷新）
 * 后台修改配置后，采集器在下一轮采集时自动生效
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectConfigService {

    private final CollectKeywordMapper keywordMapper;
    private final CollectSubredditMapper subredditMapper;

    /** 缓存有效期（毫秒）：30秒 */
    private static final long CACHE_TTL_MS = 30_000;

    private volatile List<String> cachedKeywords;
    private volatile List<String> cachedSubreddits;
    private final AtomicLong keywordCacheTime = new AtomicLong(0);
    private final AtomicLong subredditCacheTime = new AtomicLong(0);

    /**
     * 获取所有启用的采集关键词
     */
    public List<String> getEnabledKeywords() {
        long now = System.currentTimeMillis();
        if (cachedKeywords == null || now - keywordCacheTime.get() > CACHE_TTL_MS) {
            synchronized (this) {
                if (cachedKeywords == null || now - keywordCacheTime.get() > CACHE_TTL_MS) {
                    List<CollectKeyword> list = keywordMapper.selectList(
                            new LambdaQueryWrapper<CollectKeyword>()
                                    .eq(CollectKeyword::getEnabled, true)
                                    .orderByAsc(CollectKeyword::getId)
                    );
                    cachedKeywords = list.stream()
                            .map(CollectKeyword::getKeyword)
                            .collect(Collectors.toList());
                    keywordCacheTime.set(now);
                    log.info("采集关键词缓存已刷新，共 {} 个启用关键词", cachedKeywords.size());
                }
            }
        }
        return cachedKeywords;
    }

    /**
     * 获取所有启用的监控板块
     */
    public List<String> getEnabledSubreddits() {
        long now = System.currentTimeMillis();
        if (cachedSubreddits == null || now - subredditCacheTime.get() > CACHE_TTL_MS) {
            synchronized (this) {
                if (cachedSubreddits == null || now - subredditCacheTime.get() > CACHE_TTL_MS) {
                    List<CollectSubreddit> list = subredditMapper.selectList(
                            new LambdaQueryWrapper<CollectSubreddit>()
                                    .eq(CollectSubreddit::getEnabled, true)
                                    .orderByAsc(CollectSubreddit::getId)
                    );
                    cachedSubreddits = list.stream()
                            .map(CollectSubreddit::getName)
                            .collect(Collectors.toList());
                    subredditCacheTime.set(now);
                    log.info("采集板块缓存已刷新，共 {} 个启用板块", cachedSubreddits.size());
                }
            }
        }
        return cachedSubreddits;
    }

    /**
     * 手动刷新缓存（后台修改配置后调用，使变更立即生效）
     */
    public void refreshCache() {
        cachedKeywords = null;
        cachedSubreddits = null;
        keywordCacheTime.set(0);
        subredditCacheTime.set(0);
        log.info("采集配置缓存已手动清除，下次读取时重新加载");
    }

    /**
     * 增加关键词今日命中计数
     */
    public void incrementKeywordHits(String keyword) {
        CollectKeyword kw = keywordMapper.selectOne(
                new LambdaQueryWrapper<CollectKeyword>().eq(CollectKeyword::getKeyword, keyword)
        );
        if (kw != null) {
            kw.setTodayHits(kw.getTodayHits() + 1);
            keywordMapper.updateById(kw);
        }
    }

    /**
     * 更新板块最近采集时间和今日新线索数
     */
    public void updateSubredditCollected(String subredditName, int newCount) {
        CollectSubreddit sub = subredditMapper.selectOne(
                new LambdaQueryWrapper<CollectSubreddit>().eq(CollectSubreddit::getName, subredditName)
        );
        if (sub != null) {
            sub.setLastCollectedAt(java.time.LocalDateTime.now());
            sub.setTodayNew(sub.getTodayNew() + newCount);
            subredditMapper.updateById(sub);
        }
    }
}
