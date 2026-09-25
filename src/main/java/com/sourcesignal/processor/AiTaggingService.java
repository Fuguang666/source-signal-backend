package com.sourcesignal.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sourcesignal.collector.RedditCollector;
import com.sourcesignal.enums.LeadGrade;
import com.sourcesignal.enums.NeedType;
import com.sourcesignal.enums.OrderScale;
import com.sourcesignal.enums.Region;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * AI 打标分级服务
 * 调用 DeepSeek 大模型对 Reddit 采购线索进行智能打标
 *
 * 打标维度：
 * - 意向等级 S/A/B
 * - 品类识别
 * - 需求类型
 * - 订单量级
 * - 目标地区
 * - 采购意向评分
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaggingService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.endpoint:https://ark.cn-beijing.volces.com/api/v3/chat/completions}")
    private String endpoint;

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.model:deepseek-v4-1-flash-260910}")
    private String model;

    @Value("${app.ai.timeout-seconds:30}")
    private int timeoutSeconds;

    private static final String SYSTEM_PROMPT = """
            你是一个跨境采购线索分析专家。请分析以下 Reddit 帖子，判断其是否为真实采购需求，并进行结构化打标。

            打标规则：
            1. grade（意向等级）：
               - S：明确求购，给出具体品类与量级，直接询价或找供应商
               - A：有明确采购计划，询问供应商推荐或代理服务
               - B：讨论采购痛点，存在潜在需求但不明确
               - 如果帖子完全不是采购需求（如纯吐槽、技术讨论、广告），grade 设为 "REJECT"

            2. category（品类）：识别帖子中的产品品类，用中文简短描述，如"宠物用品"、"户外露营"、"3C数码"、"服装配饰"。无法识别填"未分类"

            3. needType（需求类型）：FULL_AGENT(找全链路采购代理), FACTORY(找工厂代工), QC(找质检服务), LOGISTICS(找物流清关), SUPPLY_CHAIN(找供应链合作), CONSIDERING_AGENT(考虑找代理), BEGINNER(新手入门咨询)

            4. orderScale（订单量级）：SMALL(月采<1万美金), MEDIUM(1-10万美金), LARGE(>10万美金), UNKNOWN(无法判断)

            5. region（目标地区）：NORTH_AMERICA(北美), EUROPE(欧洲), SOUTHEAST_ASIA(东南亚), AUSTRALIA(澳洲), OTHER(其他)

            6. score（采购意向评分）：0-100 分，分数越高采购意向越强

            请严格以 JSON 格式返回，不要包含任何其他文字：
            {"grade":"S","category":"品类","needType":"FULL_AGENT","orderScale":"MEDIUM","region":"NORTH_AMERICA","score":85}
            """;

    /**
     * 对原始帖子进行 AI 打标
     * @param post 原始帖子
     * @return 打标结果，如果帖子被判定为非采购需求则返回 null
     */
    public TaggingResult tagPost(RedditCollector.RawPost post) {
        String userPrompt = buildUserPrompt(post);

        try {
            String response = callAi(userPrompt);
            TaggingResult result = parseResponse(response);

            if (result == null || "REJECT".equalsIgnoreCase(result.getGrade())) {
                log.debug("帖子被判定为非采购需求: {}", post.title());
                return null;
            }

            log.info("AI 打标完成: grade={}, category={}, score={}, title={}",
                    result.getGrade(), result.getCategory(), result.getScore(),
                    post.title().length() > 50 ? post.title().substring(0, 50) + "..." : post.title());

            return result;
        } catch (Exception e) {
            log.error("AI 打标失败: {}", e.getMessage());
            return null;
        }
    }

    private String buildUserPrompt(RedditCollector.RawPost post) {
        StringBuilder sb = new StringBuilder();
        sb.append("帖子标题：").append(post.title()).append("\n");
        sb.append("帖子正文：").append(post.body() != null ? post.body() : "").append("\n");
        sb.append("发布版块：r/").append(post.subreddit()).append("\n");
        sb.append("作者：").append(post.author()).append("\n");
        return sb.toString();
    }

    private String callAi(String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", java.util.List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.1,
                "max_tokens", 2000
        );

        return webClientBuilder.build()
                .post()
                .uri(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
    }

    private TaggingResult parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            log.debug("AI 原始响应: {}", content.length() > 500 ? content.substring(0, 500) + "..." : content);

            // 提取 JSON（AI 可能在 JSON 前后有其他文字）
            String jsonStr = extractJson(content);
            JsonNode result = objectMapper.readTree(jsonStr);

            TaggingResult taggingResult = new TaggingResult();
            taggingResult.setGrade(result.path("grade").asText("B"));
            taggingResult.setCategory(result.path("category").asText("未分类"));
            taggingResult.setNeedType(result.path("needType").asText("CONSIDERING_AGENT"));
            taggingResult.setOrderScale(result.path("orderScale").asText("UNKNOWN"));
            taggingResult.setRegion(result.path("region").asText("OTHER"));
            taggingResult.setScore(result.path("score").asInt(50));

            return taggingResult;
        } catch (Exception e) {
            log.error("解析 AI 响应失败: {}", e.getMessage());
            return null;
        }
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    /**
     * 将打标结果中的字符串转换为枚举
     */
    public LeadGrade toGrade(String grade) {
        try {
            return LeadGrade.valueOf(grade.toUpperCase());
        } catch (Exception e) {
            return LeadGrade.B;
        }
    }

    public NeedType toNeedType(String needType) {
        try {
            return NeedType.valueOf(needType.toUpperCase());
        } catch (Exception e) {
            return NeedType.CONSIDERING_AGENT;
        }
    }

    public OrderScale toOrderScale(String orderScale) {
        try {
            return OrderScale.valueOf(orderScale.toUpperCase());
        } catch (Exception e) {
            return OrderScale.UNKNOWN;
        }
    }

    public Region toRegion(String region) {
        try {
            return Region.valueOf(region.toUpperCase());
        } catch (Exception e) {
            return Region.OTHER;
        }
    }

    /**
     * AI 打标结果
     */
    @Data
    @Builder
    public static class TaggingResult {
        private String grade;
        private String category;
        private String needType;
        private String orderScale;
        private String region;
        private int score;

        public TaggingResult() {}

        public TaggingResult(String grade, String category, String needType,
                             String orderScale, String region, int score) {
            this.grade = grade;
            this.category = category;
            this.needType = needType;
            this.orderScale = orderScale;
            this.region = region;
            this.score = score;
        }
    }
}
