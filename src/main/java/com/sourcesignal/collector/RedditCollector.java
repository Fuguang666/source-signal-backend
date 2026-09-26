package com.sourcesignal.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sourcesignal.service.CollectConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import reactor.util.retry.Retry;

/**
 * Reddit 数据采集器
 * 通过第三方官方授权 API 采集采购相关帖子
 *
 * 采集策略：
 * 1. 从数据库读取启用的采集关键词（后台可配置）
 * 2. 使用关键词全局搜索（仅最近24小时 t=day）
 * 3. 去重（externalId）
 * 4. 不按板块过滤，所有搜索结果均进入后续处理（由关键词初筛+AI打标过滤）
 *
 * 配置变更通过 CollectConfigService 缓存自动生效（30秒刷新）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedditCollector {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final CollectConfigService collectConfigService;

    @Value("${app.reddit.base-url:https://api.redditapis.com}")
    private String baseUrl;

    @Value("${app.reddit.api-key:}")
    private String apiKey;

    /** 默认关键词（数据库为空时的 fallback） */
    private static final List<String> DEFAULT_KEYWORDS = Arrays.asList(
            "sourcing agent",
            "looking for supplier",
            "looking for manufacturer",
            "wholesale supplier",
            "buying from china",
            "china sourcing",
            "find factory china",
            "private label manufacturer",
            "alibaba supplier",
            "product sourcing"
    );

    /**
     * 使用关键词采集帖子（不按板块过滤）
     * @return 原始帖子列表（已去重）
     */
    public List<RawPost> collectAll() {
        // 从数据库读取启用的关键词（带缓存）
        List<String> keywords = collectConfigService.getEnabledKeywords();

        // 数据库为空时使用默认配置
        if (keywords.isEmpty()) {
            log.warn("数据库中无启用的采集关键词，使用默认配置");
            keywords = DEFAULT_KEYWORDS;
        }

        log.info("本轮采集配置：{} 个关键词（不按板块过滤）", keywords.size());

        List<RawPost> allPosts = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (String keyword : keywords) {
            try {
                List<RawPost> posts = searchPosts(keyword, 50);
                int added = 0;
                for (RawPost post : posts) {
                    // 仅去重，不按板块过滤
                    if (!seenIds.contains(post.externalId())) {
                        seenIds.add(post.externalId());
                        allPosts.add(post);
                        added++;
                    }
                }
                // 更新关键词命中计数
                if (added > 0) {
                    collectConfigService.incrementKeywordHits(keyword);
                }
                log.info("关键词 '{}' 搜索到 {} 条，去重后新增 {} 条", keyword, posts.size(), added);
            } catch (Exception e) {
                log.error("关键词 '{}' 采集失败", keyword, e);
            }
            // 关键词之间短暂间隔，避免连续快速请求导致 API 限流或连接异常
            try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }

        log.info("本轮采集完成，共获取 {} 条去重后的帖子", allPosts.size());
        return allPosts;
    }

    /**
     * 搜索帖子
     */
    public List<RawPost> searchPosts(String query, int limit) {
        String url = baseUrl + "/api/reddit/search?q=" + encodeUrl(query) + "&limit=" + limit + "&t=day";

        String response = webClientBuilder.build()
                .get()
                .uri(url)
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(3)).maxBackoff(Duration.ofSeconds(10)))
                .block();

        return parsePosts(response);
    }

    /**
     * 按 ID 批量获取帖子详情
     */
    public List<RawPost> getPostsByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        String idParam = String.join(",", ids);
        String url = baseUrl + "/api/reddit/by_id/" + idParam;

        String response = webClientBuilder.build()
                .get()
                .uri(url)
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return parsePosts(response);
    }

    private List<RawPost> parsePosts(String response) {
        List<RawPost> posts = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode postsNode = root.path("posts");
            if (postsNode.isArray()) {
                for (JsonNode node : postsNode) {
                    try {
                        RawPost post = parseSinglePost(node);
                        if (post != null) {
                            posts.add(post);
                        }
                    } catch (Exception e) {
                        log.warn("解析帖子失败: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析 Reddit 响应失败", e);
        }
        return posts;
    }

    private RawPost parseSinglePost(JsonNode node) {
        String externalId = node.path("name").asText();
        if (externalId.isEmpty()) {
            externalId = "t3_" + node.path("id").asText();
        }

        String title = node.path("title").asText("");
        String body = node.path("text").asText("");
        String subreddit = node.path("subreddit").asText("");
        String author = node.path("author").asText("");
        String url = node.path("url").asText("");
        if (url.isEmpty()) {
            url = "https://reddit.com" + node.path("permalink").asText("");
        }

        // 解析时间
        LocalDateTime postedAt = LocalDateTime.now();
        String createdStr = node.path("created").asText("");
        if (!createdStr.isEmpty()) {
            try {
                postedAt = LocalDateTime.ofInstant(Instant.parse(createdStr), ZoneId.of("Asia/Shanghai"));
            } catch (Exception e) {
                long createdUtc = node.path("created_utc").asLong(0);
                if (createdUtc > 0) {
                    postedAt = LocalDateTime.ofInstant(Instant.ofEpochSecond(createdUtc), ZoneId.of("Asia/Shanghai"));
                }
            }
        }

        // 作者 karma（API 未直接提供，用 upvotes 近似；账号去噪已禁用，此字段暂不使用）
        int authorKarma = node.path("upvotes").asInt(0);

        return new RawPost(externalId, subreddit, title, body, url, author, authorKarma, postedAt);
    }

    private String encodeUrl(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 原始帖子数据结构（采集层输出）
     */
    public record RawPost(
            String externalId,
            String subreddit,
            String title,
            String body,
            String url,
            String author,
            Integer authorKarma,
            LocalDateTime postedAt
    ) {}
}
