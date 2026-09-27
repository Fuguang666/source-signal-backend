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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    /** 固定品类列表（AI 必须从中选择，不允许自由发挥或填"未分类"） */
    private static final String[] CATEGORY_LIST = {
            "宠物用品", "户外露营", "3C数码", "服装配饰", "家居用品",
            "美妆个护", "母婴玩具", "运动健身", "汽车配件", "工业五金",
            "食品饮料", "图书文具", "珠宝饰品", "电子产品", "医疗器械",
            "办公用品", "其他"
    };

    /** 品类关键词映射（英文关键词 -> 中文品类），用于 AI 返回空/未分类时的兜底匹配 */
    private static final Map<String, String> CATEGORY_KEYWORDS = new HashMap<>();
    static {
        // 宠物用品
        CATEGORY_KEYWORDS.put("pet", "宠物用品");
        CATEGORY_KEYWORDS.put("dog", "宠物用品");
        CATEGORY_KEYWORDS.put("cat", "宠物用品");
        CATEGORY_KEYWORDS.put("puppy", "宠物用品");
        CATEGORY_KEYWORDS.put("kitten", "宠物用品");
        CATEGORY_KEYWORDS.put("pet supply", "宠物用品");
        // 户外露营
        CATEGORY_KEYWORDS.put("camping", "户外露营");
        CATEGORY_KEYWORDS.put("outdoor", "户外露营");
        CATEGORY_KEYWORDS.put("hiking", "户外露营");
        CATEGORY_KEYWORDS.put("tent", "户外露营");
        CATEGORY_KEYWORDS.put("backpacking", "户外露营");
        CATEGORY_KEYWORDS.put("survival", "户外露营");
        // 3C数码
        CATEGORY_KEYWORDS.put("phone", "3C数码");
        CATEGORY_KEYWORDS.put("smartphone", "3C数码");
        CATEGORY_KEYWORDS.put("laptop", "3C数码");
        CATEGORY_KEYWORDS.put("computer", "3C数码");
        CATEGORY_KEYWORDS.put("tablet", "3C数码");
        CATEGORY_KEYWORDS.put("headphone", "3C数码");
        CATEGORY_KEYWORDS.put("earphone", "3C数码");
        CATEGORY_KEYWORDS.put("charger", "3C数码");
        CATEGORY_KEYWORDS.put("cable", "3C数码");
        CATEGORY_KEYWORDS.put("usb", "3C数码");
        // 服装配饰
        CATEGORY_KEYWORDS.put("clothing", "服装配饰");
        CATEGORY_KEYWORDS.put("apparel", "服装配饰");
        CATEGORY_KEYWORDS.put("garment", "服装配饰");
        CATEGORY_KEYWORDS.put("t-shirt", "服装配饰");
        CATEGORY_KEYWORDS.put("shirt", "服装配饰");
        CATEGORY_KEYWORDS.put("dress", "服装配饰");
        CATEGORY_KEYWORDS.put("jacket", "服装配饰");
        CATEGORY_KEYWORDS.put("hoodie", "服装配饰");
        CATEGORY_KEYWORDS.put("sneaker", "服装配饰");
        CATEGORY_KEYWORDS.put("shoe", "服装配饰");
        CATEGORY_KEYWORDS.put("bag", "服装配饰");
        CATEGORY_KEYWORDS.put("handbag", "服装配饰");
        CATEGORY_KEYWORDS.put("watch", "服装配饰");
        CATEGORY_KEYWORDS.put("sunglasses", "服装配饰");
        // 家居用品
        CATEGORY_KEYWORDS.put("home", "家居用品");
        CATEGORY_KEYWORDS.put("furniture", "家居用品");
        CATEGORY_KEYWORDS.put("kitchen", "家居用品");
        CATEGORY_KEYWORDS.put("bedding", "家居用品");
        CATEGORY_KEYWORDS.put("curtain", "家居用品");
        CATEGORY_KEYWORDS.put("lamp", "家居用品");
        CATEGORY_KEYWORDS.put("decor", "家居用品");
        CATEGORY_KEYWORDS.put("garden", "家居用品");
        // 美妆个护
        CATEGORY_KEYWORDS.put("beauty", "美妆个护");
        CATEGORY_KEYWORDS.put("cosmetic", "美妆个护");
        CATEGORY_KEYWORDS.put("skincare", "美妆个护");
        CATEGORY_KEYWORDS.put("makeup", "美妆个护");
        CATEGORY_KEYWORDS.put("fragrance", "美妆个护");
        CATEGORY_KEYWORDS.put("perfume", "美妆个护");
        CATEGORY_KEYWORDS.put("hair care", "美妆个护");
        CATEGORY_KEYWORDS.put("shampoo", "美妆个护");
        // 母婴玩具
        CATEGORY_KEYWORDS.put("baby", "母婴玩具");
        CATEGORY_KEYWORDS.put("infant", "母婴玩具");
        CATEGORY_KEYWORDS.put("newborn", "母婴玩具");
        CATEGORY_KEYWORDS.put("toy", "母婴玩具");
        CATEGORY_KEYWORDS.put("kids", "母婴玩具");
        CATEGORY_KEYWORDS.put("children", "母婴玩具");
        CATEGORY_KEYWORDS.put("diaper", "母婴玩具");
        CATEGORY_KEYWORDS.put("stroller", "母婴玩具");
        // 运动健身
        CATEGORY_KEYWORDS.put("fitness", "运动健身");
        CATEGORY_KEYWORDS.put("gym", "运动健身");
        CATEGORY_KEYWORDS.put("workout", "运动健身");
        CATEGORY_KEYWORDS.put("yoga", "运动健身");
        CATEGORY_KEYWORDS.put("sport", "运动健身");
        CATEGORY_KEYWORDS.put("running", "运动健身");
        CATEGORY_KEYWORDS.put("cycling", "运动健身");
        CATEGORY_KEYWORDS.put("dumbbell", "运动健身");
        // 汽车配件
        CATEGORY_KEYWORDS.put("car", "汽车配件");
        CATEGORY_KEYWORDS.put("auto", "汽车配件");
        CATEGORY_KEYWORDS.put("automotive", "汽车配件");
        CATEGORY_KEYWORDS.put("vehicle", "汽车配件");
        CATEGORY_KEYWORDS.put("motorcycle", "汽车配件");
        CATEGORY_KEYWORDS.put("tire", "汽车配件");
        CATEGORY_KEYWORDS.put("battery", "汽车配件");
        // 工业五金
        CATEGORY_KEYWORDS.put("industrial", "工业五金");
        CATEGORY_KEYWORDS.put("hardware", "工业五金");
        CATEGORY_KEYWORDS.put("tool", "工业五金");
        CATEGORY_KEYWORDS.put("machinery", "工业五金");
        CATEGORY_KEYWORDS.put("manufacturing", "工业五金");
        CATEGORY_KEYWORDS.put("metal", "工业五金");
        CATEGORY_KEYWORDS.put("steel", "工业五金");
        CATEGORY_KEYWORDS.put("plastic", "工业五金");
        CATEGORY_KEYWORDS.put("mold", "工业五金");
        // 食品饮料
        CATEGORY_KEYWORDS.put("food", "食品饮料");
        CATEGORY_KEYWORDS.put("beverage", "食品饮料");
        CATEGORY_KEYWORDS.put("drink", "食品饮料");
        CATEGORY_KEYWORDS.put("snack", "食品饮料");
        CATEGORY_KEYWORDS.put("coffee", "食品饮料");
        CATEGORY_KEYWORDS.put("tea", "食品饮料");
        CATEGORY_KEYWORDS.put("supplement", "食品饮料");
        // 图书文具
        CATEGORY_KEYWORDS.put("book", "图书文具");
        CATEGORY_KEYWORDS.put("stationery", "图书文具");
        CATEGORY_KEYWORDS.put("pen", "图书文具");
        CATEGORY_KEYWORDS.put("notebook", "图书文具");
        CATEGORY_KEYWORDS.put("paper", "图书文具");
        CATEGORY_KEYWORDS.put("print", "图书文具");
        // 珠宝饰品
        CATEGORY_KEYWORDS.put("jewelry", "珠宝饰品");
        CATEGORY_KEYWORDS.put("jewellery", "珠宝饰品");
        CATEGORY_KEYWORDS.put("ring", "珠宝饰品");
        CATEGORY_KEYWORDS.put("necklace", "珠宝饰品");
        CATEGORY_KEYWORDS.put("earring", "珠宝饰品");
        CATEGORY_KEYWORDS.put("bracelet", "珠宝饰品");
        CATEGORY_KEYWORDS.put("diamond", "珠宝饰品");
        CATEGORY_KEYWORDS.put("gold", "珠宝饰品");
        CATEGORY_KEYWORDS.put("silver", "珠宝饰品");
        // 电子产品
        CATEGORY_KEYWORDS.put("electronic", "电子产品");
        CATEGORY_KEYWORDS.put("device", "电子产品");
        CATEGORY_KEYWORDS.put("gadget", "电子产品");
        CATEGORY_KEYWORDS.put("drone", "电子产品");
        CATEGORY_KEYWORDS.put("camera", "电子产品");
        CATEGORY_KEYWORDS.put("speaker", "电子产品");
        CATEGORY_KEYWORDS.put("smart", "电子产品");
        CATEGORY_KEYWORDS.put("wearable", "电子产品");
        // 医疗器械
        CATEGORY_KEYWORDS.put("medical", "医疗器械");
        CATEGORY_KEYWORDS.put("health", "医疗器械");
        CATEGORY_KEYWORDS.put("hospital", "医疗器械");
        CATEGORY_KEYWORDS.put("clinic", "医疗器械");
        CATEGORY_KEYWORDS.put("surgical", "医疗器械");
        CATEGORY_KEYWORDS.put("dental", "医疗器械");
        // 办公用品
        CATEGORY_KEYWORDS.put("office", "办公用品");
        CATEGORY_KEYWORDS.put("desk", "办公用品");
        CATEGORY_KEYWORDS.put("chair", "办公用品");
        CATEGORY_KEYWORDS.put("printer", "办公用品");
        CATEGORY_KEYWORDS.put("scanner", "办公用品");
    }

    private static final String SYSTEM_PROMPT = """
            你是一个跨境采购线索分析专家。请分析以下 Reddit 帖子，判断其是否为真实采购需求，并进行结构化打标。

            打标规则：
            1. grade（意向等级）：
               - S：明确求购，给出具体品类与量级，直接询价或找供应商
               - A：有明确采购计划，询问供应商推荐或代理服务
               - B：讨论采购痛点，存在潜在需求但不明确
               - 如果帖子完全不是采购需求（如纯吐槽、技术讨论、广告），grade 设为 "REJECT"

            2. category（品类）：必须从以下固定列表中选择最接近的一个，用中文填写，不允许填列表以外的值，不允许填"未分类"或空值：
               宠物用品、户外露营、3C数码、服装配饰、家居用品、美妆个护、母婴玩具、运动健身、汽车配件、工业五金、食品饮料、图书文具、珠宝饰品、电子产品、医疗器械、办公用品、其他
               选择标准：根据帖子中提到的产品名称、行业关键词判断最接近的品类。如果帖子涉及多个品类，选择占比最大或最核心的那个。

            3. needType（需求类型）：FULL_AGENT(找全链路采购代理), FACTORY(找工厂代工), QC(找质检服务), LOGISTICS(找物流清关), SUPPLY_CHAIN(找供应链合作), CONSIDERING_AGENT(考虑找代理), BEGINNER(新手入门咨询)

            4. orderScale（订单量级）：SMALL(月采<1万美金), MEDIUM(1-10万美金), LARGE(>10万美金), UNKNOWN(无法判断)

            5. region（目标地区）：NORTH_AMERICA(北美), EUROPE(欧洲), SOUTHEAST_ASIA(东南亚), AUSTRALIA(澳洲), OTHER(其他)

            6. score（采购意向评分）：0-100 分，分数越高采购意向越强

            请严格以 JSON 格式返回，不要包含任何其他文字：
            {"grade":"S","category":"宠物用品","needType":"FULL_AGENT","orderScale":"MEDIUM","region":"NORTH_AMERICA","score":85}
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

            // 兜底处理：如果 AI 返回的品类为空或"未分类"，用标题关键词匹配
            if (result.getCategory() == null || result.getCategory().isBlank()
                    || "未分类".equals(result.getCategory()) || "unknown".equalsIgnoreCase(result.getCategory())) {
                String fallback = fallbackCategory(post.title(), post.body());
                log.warn("AI 品类识别为空/未分类，使用关键词兜底匹配: title={}, fallback={}",
                        post.title().length() > 50 ? post.title().substring(0, 50) + "..." : post.title(), fallback);
                result.setCategory(fallback);
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

    /**
     * 基于标题和正文关键词的品类兜底匹配
     * @param title 帖子标题
     * @param body 帖子正文
     * @return 匹配到的品类，无匹配时返回"其他"
     */
    private String fallbackCategory(String title, String body) {
        String text = ((title != null ? title : "") + " " + (body != null ? body : "")).toLowerCase();
        Map<String, Integer> categoryCount = new HashMap<>();

        for (Map.Entry<String, String> entry : CATEGORY_KEYWORDS.entrySet()) {
            if (text.contains(entry.getKey())) {
                categoryCount.merge(entry.getValue(), 1, Integer::sum);
            }
        }

        if (categoryCount.isEmpty()) {
            return "其他";
        }

        // 返回命中关键词最多的品类
        return categoryCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("其他");
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
            taggingResult.setCategory(result.path("category").asText(""));
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
