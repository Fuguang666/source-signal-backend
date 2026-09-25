package com.sourcesignal.scheduler;

import com.sourcesignal.collector.RedditCollector;
import com.sourcesignal.processor.LeadProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 数据采集定时任务
 * 定期从 Reddit 采集新帖，经处理后入库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectionScheduler {

    private final RedditCollector redditCollector;
    private final LeadProcessor leadProcessor;

    @Value("${app.reddit.poll-interval-seconds:300}")
    private int pollIntervalSeconds;

    /** 采集运行状态（防止并发执行） */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 最后一次采集时间 */
    private volatile LocalDateTime lastCollectionAt;

    /** 最后一次采集入库数量 */
    private volatile int lastSavedCount;

    /**
     * 定时采集 Reddit 新帖
     * 默认每 5 分钟执行一次
     *
     * 采集流水线：
     * 1. RedditCollector 采集原始帖子
     * 2. LeadProcessor 初筛 → 去噪 → 去重 → AI打标 → 入库
     */
    @Scheduled(fixedDelayString = "${app.reddit.poll-interval-seconds:300}000")
    public void scheduledCollection() {
        triggerCollection("定时任务");
    }

    /**
     * 手动触发采集（供 API 调用）
     */
    public CollectionResult triggerCollection(String triggerSource) {
        if (!running.compareAndSet(false, true)) {
            log.warn("采集任务正在运行中，跳过本次触发: source={}", triggerSource);
            return new CollectionResult(false, 0, 0, "采集任务正在运行中");
        }

        long startTime = System.currentTimeMillis();
        int collected = 0;
        int saved = 0;

        try {
            log.info("===== 采集任务开始 [来源: {}] =====", triggerSource);

            // 1. 采集原始帖子
            List<RedditCollector.RawPost> rawPosts = redditCollector.collectAll();
            collected = rawPosts.size();

            // 2. 处理并入库
            saved = leadProcessor.processRawPosts(rawPosts);

            long duration = System.currentTimeMillis() - startTime;
            lastCollectionAt = LocalDateTime.now();
            lastSavedCount = saved;

            log.info("===== 采集任务完成: 采集={}, 入库={}, 耗时={}ms =====",
                    collected, saved, duration);

            return new CollectionResult(true, collected, saved, "采集完成");

        } catch (Exception e) {
            log.error("采集任务失败", e);
            return new CollectionResult(false, collected, saved, "采集失败: " + e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /**
     * 获取采集状态
     */
    public CollectionStatus getStatus() {
        return new CollectionStatus(
                running.get(),
                lastCollectionAt,
                lastSavedCount,
                pollIntervalSeconds
        );
    }

    /**
     * 每日重置试用版样例线索计数
     * 每天 00:05 执行
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void resetDailySampleCount() {
        log.info("执行每日样例线索计数重置");
        // TODO: 重置所有试用版用户的 todaySampleCount
    }

    /**
     * 试用期到期检查
     * 每天 01:00 执行
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void checkTrialExpiration() {
        log.info("执行试用期到期检查");
        // TODO: 检查到期用户
    }

    /**
     * 采集结果
     */
    public record CollectionResult(boolean success, int collected, int saved, String message) {}

    /**
     * 采集状态
     */
    public record CollectionStatus(boolean running, LocalDateTime lastCollectionAt,
                                   int lastSavedCount, int pollIntervalSeconds) {}
}
