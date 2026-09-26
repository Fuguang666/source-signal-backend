package com.sourcesignal.processor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sourcesignal.collector.RedditCollector;
import com.sourcesignal.entity.Lead;
import com.sourcesignal.mapper.LeadMapper;
import com.sourcesignal.service.PushService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线索处理器
 * 负责：关键词初筛 → 批量去重 → AI 结构化打标（并行） → S/A/B 意向分级 → 入库 → 推送
 *
 * 优化：AI打标采用线程池并行处理（默认4线程），相比串行处理速度提升约3-4倍。
 * 初筛和去重为CPU/IO轻量操作，保持串行以简化逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeadProcessor {

    private final LeadMapper leadMapper;
    private final AiTaggingService aiTaggingService;
    private final PushService pushService;

    /** AI打标并行线程数（可配置） */
    @Value("${app.ai.tagging-parallelism:4}")
    private int taggingParallelism;

    /** AI打标线程池（懒加载初始化） */
    private volatile ExecutorService taggingExecutor;

    /** 行业关键词库（初筛用，过滤约 90% 无关内容） */
    private static final Set<String> KEYWORD_DICTIONARY = Set.of(
            "sourcing", "sourcing agent", "factory", "manufacturer", "supplier",
            "china", "alibaba", "oem", "odm", "moq", "wholesale", "import",
            "freight", "forwarder", "qc", "quality inspection", "customs",
            "fba", "amazon fba", "private label", "product sourcing",
            "find a", "looking for", "need a", "want to buy", "bulk order"
    );

    /** 排除关键词（广告帖、吐槽帖、招聘帖、纯闲聊帖） */
    private static final Set<String> EXCLUDE_KEYWORDS = Set.of(
            "selling", "for sale", "hire me", "looking for work",
            "promo code", "discount code", "spam", "check out my",
            "i am selling", "we sell", "buy from me"
    );

    /**
     * 获取或创建AI打标线程池（双重检查锁）
     */
    private ExecutorService getTaggingExecutor() {
        if (taggingExecutor == null) {
            synchronized (this) {
                if (taggingExecutor == null) {
                    taggingExecutor = Executors.newFixedThreadPool(taggingParallelism, r -> {
                        Thread t = new Thread(r, "ai-tagging-worker");
                        t.setDaemon(true);
                        return t;
                    });
                    log.info("AI打标线程池已初始化，并行度={}", taggingParallelism);
                }
            }
        }
        return taggingExecutor;
    }

    @PreDestroy
    public void shutdown() {
        if (taggingExecutor != null) {
            taggingExecutor.shutdown();
            log.info("AI打标线程池已关闭");
        }
    }

    /**
     * 处理一批原始帖子，返回入库数量
     * 流程：串行初筛 → 批量去重 → 并行AI打标+入库
     */
    public int processRawPosts(List<RedditCollector.RawPost> rawPosts) {
        AtomicInteger savedCount = new AtomicInteger(0);
        AtomicInteger filteredCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        // ===== 阶段1：串行初筛（关键词初筛 + 账号去噪）=====
        List<RedditCollector.RawPost> candidates = new ArrayList<>();
        for (RedditCollector.RawPost rawPost : rawPosts) {
            try {
                if (!keywordFilter(rawPost.title(), rawPost.body())) {
                    filteredCount.incrementAndGet();
                    continue;
                }
                if (!accountFilter(rawPost.author(), rawPost.authorKarma())) {
                    filteredCount.incrementAndGet();
                    continue;
                }
                candidates.add(rawPost);
            } catch (Exception e) {
                log.error("初筛帖子失败: externalId={}, error={}", rawPost.externalId(), e.getMessage());
            }
        }
        log.info("初筛完成: 总数={}, 通过初筛={}, 过滤={}", rawPosts.size(), candidates.size(), filteredCount.get());

        // ===== 阶段2：批量去重（查询数据库中已存在的 externalId）=====
        if (!candidates.isEmpty()) {
            Set<String> existingIds = batchQueryExistingIds(candidates);
            int beforeSize = candidates.size();
            candidates.removeIf(p -> existingIds.contains(p.externalId()));
            int dupCount = beforeSize - candidates.size();
            filteredCount.addAndGet(dupCount);
            if (dupCount > 0) {
                log.info("数据库去重: 移除 {} 条已存在的线索", dupCount);
            }
        }

        if (candidates.isEmpty()) {
            log.info("本轮处理完成: 总数={}, 入库=0, 过滤={}, AI拒绝=0（无新线索需打标）",
                    rawPosts.size(), filteredCount.get());
            return 0;
        }

        // ===== 阶段3：并行AI打标 + 入库 + 推送 =====
        log.info("开始并行AI打标: 待处理={} 条, 并行度={}", candidates.size(), taggingParallelism);
        long startTime = System.currentTimeMillis();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (RedditCollector.RawPost rawPost : candidates) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // AI 打标分级
                    AiTaggingService.TaggingResult taggingResult = aiTaggingService.tagPost(rawPost);
                    if (taggingResult == null) {
                        rejectedCount.incrementAndGet();
                        return;
                    }

                    // 构建 Lead 实体并入库
                    Lead lead = buildLead(rawPost, taggingResult);
                    leadMapper.insert(lead);
                    savedCount.incrementAndGet();

                    log.info("线索入库: grade={}, category={}, score={}, title={}",
                            lead.getGrade(), lead.getCategory(), taggingResult.getScore(),
                            truncate(lead.getTitle(), 60));

                    // 站内推送
                    try {
                        pushService.pushNewLead(lead);
                    } catch (Exception e) {
                        log.warn("站内推送失败（不影响入库）: leadId={}, error={}", lead.getId(), e.getMessage());
                    }

                } catch (Exception e) {
                    log.error("处理帖子失败: externalId={}, error={}", rawPost.externalId(), e.getMessage());
                }
            }, getTaggingExecutor());
            futures.add(future);
        }

        // 等待所有并行任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("并行AI打标完成: 待处理={}, 耗时={}ms (平均 {}ms/条)",
                candidates.size(), elapsed, candidates.size() > 0 ? elapsed / candidates.size() : 0);

        log.info("本轮处理完成: 总数={}, 入库={}, 过滤={}, AI拒绝={}",
                rawPosts.size(), savedCount.get(), filteredCount.get(), rejectedCount.get());

        return savedCount.get();
    }

    /**
     * 批量查询数据库中已存在的 externalId
     * 分批查询（每批500个），避免SQL过长
     */
    private Set<String> batchQueryExistingIds(List<RedditCollector.RawPost> posts) {
        Set<String> existingIds = new HashSet<>();
        List<String> allIds = new ArrayList<>();
        for (RedditCollector.RawPost post : posts) {
            allIds.add(post.externalId());
        }

        int batchSize = 500;
        for (int i = 0; i < allIds.size(); i += batchSize) {
            List<String> batch = allIds.subList(i, Math.min(i + batchSize, allIds.size()));
            try {
                List<Lead> existing = leadMapper.selectList(new LambdaQueryWrapper<Lead>()
                        .in(Lead::getExternalId, batch)
                        .select(Lead::getExternalId));
                for (Lead lead : existing) {
                    existingIds.add(lead.getExternalId());
                }
            } catch (Exception e) {
                log.error("批量查询已存在线索失败: batchSize={}, error={}", batch.size(), e.getMessage());
            }
        }
        return existingIds;
    }

    private Lead buildLead(RedditCollector.RawPost rawPost, AiTaggingService.TaggingResult result) {
        return Lead.builder()
                .externalId(rawPost.externalId())
                .subreddit(rawPost.subreddit())
                .title(rawPost.title())
                .body(rawPost.body())
                .sourceUrl(rawPost.url())
                .author(rawPost.author())
                .authorKarma(rawPost.authorKarma())
                .grade(aiTaggingService.toGrade(result.getGrade()))
                .category(result.getCategory())
                .orderScale(aiTaggingService.toOrderScale(result.getOrderScale()))
                .needType(aiTaggingService.toNeedType(result.getNeedType()))
                .region(aiTaggingService.toRegion(result.getRegion()))
                .postedAt(rawPost.postedAt())
                .collectedAt(LocalDateTime.now())
                .taggedAt(LocalDateTime.now())
                .reviewed(false)
                .build();
    }

    /**
     * 关键词库规则初筛
     * @return true = 通过初筛（相关内容），false = 过滤掉
     */
    public boolean keywordFilter(String title, String body) {
        String content = (title + " " + (body != null ? body : "")).toLowerCase();

        // 排除无关内容
        for (String keyword : EXCLUDE_KEYWORDS) {
            if (content.contains(keyword)) {
                return false;
            }
        }

        // 匹配行业关键词
        for (String keyword : KEYWORD_DICTIONARY) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 账号去噪
     * 注意：Reddit API 的 author_info 中不包含作者 karma，upvotes 是帖子点赞数（非作者karma），
     * 误用 upvotes 作为 karma 会导致新帖子（点赞数<10）全部被过滤。
     * 暂时禁用账号去噪，后续可通过单独查询作者信息获取真实 karma。
     */
    public boolean accountFilter(String author, Integer authorKarma) {
        // 暂时禁用：API 不提供作者 karma，无法准确判断
        return true;
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}
