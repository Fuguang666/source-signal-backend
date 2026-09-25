package com.sourcesignal.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reddit 数据采集器
 * 通过第三方官方授权 API 采集采购相关帖子
 *
 * 采集策略：
 * 1. 使用多个采购相关关键词搜索
 * 2. 过滤出目标 subreddit 的帖子
 * 3. 去重（externalId）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedditCollector {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${app.reddit.base-url:https://api.redditapis.com}")
    private String baseUrl;

    @Value("${app.reddit.api-key:}")
    private String apiKey;

    @Value("${app.reddit.subreddits:ChinaSourcing,Business_China,ecommerce,AmazonSeller}")
    private String subreddits;

    /** 采购相关搜索关键词 */
    private static final List<String> SEARCH_KEYWORDS = Arrays.asList(
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
     * 采集所有目标 subreddit 的新帖
     * @return 原始帖子列表（已去重）
     */
    public List<RawPost> collectAll() {
        Set<String> targetSubs = new HashSet<>(Arrays.asList(subreddits.split(",")));
        // 额外关注一些采购相关 subreddit
        targetSubs.addAll(Arrays.asList("dropshipping", "FulfillmentByAmazon", "Entrepreneur", "smallbusiness"));

        List<RawPost> allPosts = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (String keyword : SEARCH_KEYWORDS) {
            try {
                List<RawPost> posts = searchPosts(keyword, 25);
                for (RawPost post : posts) {
                    // 只保留目标 subreddit 的帖子
                    if (targetSubs.contains(post.subreddit()) && !seenIds.contains(post.externalId())) {
                        seenIds.add(post.externalId());
                        allPosts.add(post);
                    }
                }
                log.info("关键词 '{}' 采集到 {} 条目标帖子", keyword, posts.size());
            } catch (Exception e) {
                log.error("关键词 '{}' 采集失败", keyword, e);
            }
        }

        log.info("本轮采集完成，共获取 {} 条去重后的目标帖子", allPosts.size());
        return allPosts;
    }

    /**
     * 搜索帖子
     */
    public List<RawPost> searchPosts(String query, int limit) {
        String url = baseUrl + "/api/reddit/search?q=" + encodeUrl(query) + "&limit=" + limit;

        String response = webClientBuilder.build()
                .get()
                .uri(url)
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
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

        // 作者 karma（API 未直接提供，用 upvotes 近似）
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
