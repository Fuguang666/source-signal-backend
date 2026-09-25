package com.sourcesignal.processor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sourcesignal.collector.RedditCollector;
import com.sourcesignal.entity.Lead;
import com.sourcesignal.enums.LeadGrade;
import com.sourcesignal.enums.NeedType;
import com.sourcesignal.enums.OrderScale;
import com.sourcesignal.enums.Region;
import com.sourcesignal.mapper.LeadMapper;
import com.sourcesignal.service.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线索处理器
 * 负责：关键词初筛 → 账号去噪 → 去重 → AI 结构化打标 → S/A/B 意向分级 → 入库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeadProcessor {

    private final LeadMapper leadMapper;
    private final AiTaggingService aiTaggingService;
    private final PushService pushService;

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
     * 处理一批原始帖子，返回入库数量
     */
    public int processRawPosts(List<RedditCollector.RawPost> rawPosts) {
        AtomicInteger savedCount = new AtomicInteger(0);
        AtomicInteger filteredCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (RedditCollector.RawPost rawPost : rawPosts) {
            try {
                // 1. 关键词初筛
                if (!keywordFilter(rawPost.title(), rawPost.body())) {
                    filteredCount.incrementAndGet();
                    continue;
                }

                // 2. 账号去噪
                if (!accountFilter(rawPost.author(), rawPost.authorKarma())) {
                    filteredCount.incrementAndGet();
                    continue;
                }

                // 3. 去重（同一 externalId 不重复入库）
                Long existsCount = leadMapper.selectCount(new LambdaQueryWrapper<Lead>()
                        .eq(Lead::getExternalId, rawPost.externalId()));
                if (existsCount != null && existsCount > 0) {
                    filteredCount.incrementAndGet();
                    continue;
                }

                // 4. AI 打标分级
                AiTaggingService.TaggingResult taggingResult = aiTaggingService.tagPost(rawPost);
                if (taggingResult == null) {
                    rejectedCount.incrementAndGet();
                    continue;
                }

                // 5. 构建 Lead 实体并入库
                Lead lead = buildLead(rawPost, taggingResult);
                leadMapper.insert(lead);
                savedCount.incrementAndGet();

                log.info("线索入库: grade={}, category={}, score={}, title={}",
                        lead.getGrade(), lead.getCategory(), taggingResult.getScore(),
                        truncate(lead.getTitle(), 60));

                // 6. 站内推送（异步触发，不阻塞主流程）
                try {
                    pushService.pushNewLead(lead);
                } catch (Exception e) {
                    log.warn("站内推送失败（不影响入库）: leadId={}, error={}", lead.getId(), e.getMessage());
                }

            } catch (Exception e) {
                log.error("处理帖子失败: externalId={}, error={}", rawPost.externalId(), e.getMessage());
            }
        }

        log.info("本轮处理完成: 总数={}, 入库={}, 过滤={}, AI拒绝={}",
                rawPosts.size(), savedCount.get(), filteredCount.get(), rejectedCount.get());

        return savedCount.get();
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
     * 过滤新号、Karma < 10 的账号
     */
    public boolean accountFilter(String author, Integer authorKarma) {
        if (authorKarma != null && authorKarma < 10) {
            log.debug("账号去噪过滤: author={}, karma={}", author, authorKarma);
            return false;
        }
        return true;
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}
