package com.sourcesignal.service;

import com.sourcesignal.dto.DashboardDTO;
import com.sourcesignal.dto.LeadDTO;
import com.sourcesignal.entity.Lead;
import com.sourcesignal.entity.Subscription;
import com.sourcesignal.entity.UserSubscriptionConfig;
import com.sourcesignal.enums.LeadGrade;
import com.sourcesignal.enums.PlanType;
import com.sourcesignal.enums.SubscriptionStatus;
import com.sourcesignal.repository.LeadRepository;
import com.sourcesignal.repository.SubscriptionRepository;
import com.sourcesignal.repository.UserLeadRepository;
import com.sourcesignal.repository.UserSubscriptionConfigRepository;
import com.sourcesignal.security.CurrentUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 仪表盘服务
 * 提供首页统计、分级分布、品类趋势、采集趋势等数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final LeadRepository leadRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserSubscriptionConfigRepository configRepository;
    private final UserLeadRepository userLeadRepository;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    @Value("${app.trial.daily-leads:3}")
    private int dailySampleLimit;

    public DashboardDTO getDashboard() {
        Long userId = currentUser.getCurrentUserId();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        // ===== 基础统计 =====
        long todayNew = leadRepository.countByCollectedAtAfter(todayStart);
        long todaySGrade = leadRepository.countByGradeAndCollectedAtAfter(LeadGrade.S, todayStart);
        long totalLeads = leadRepository.count();
        long readCount = userLeadRepository.countByUserIdAndReadTrue(userId);
        long unreadCount = Math.max(0, totalLeads - readCount);

        // ===== 订阅信息 =====
        Subscription subscription = subscriptionRepository.findByUserId(userId).orElse(null);
        long trialDaysLeft = 0;
        int todaySampleUsed = 0;
        boolean isPaid = false;
        if (subscription != null) {
            isPaid = subscription.getPlanType() == PlanType.PAID
                    && subscription.getStatus() == SubscriptionStatus.ACTIVE;
            if (subscription.getStatus() == SubscriptionStatus.TRIAL && subscription.getTrialEndDate() != null) {
                trialDaysLeft = ChronoUnit.DAYS.between(LocalDate.now(), subscription.getTrialEndDate());
                trialDaysLeft = Math.max(0, trialDaysLeft);
            }
            todaySampleUsed = subscription.getTodaySampleCount() != null ? subscription.getTodaySampleCount() : 0;
        }

        // ===== 用户监控配置 =====
        List<String> categories = new ArrayList<>();
        List<String> regions = new ArrayList<>();
        Optional<UserSubscriptionConfig> configOpt = configRepository.findByUserId(userId);
        if (configOpt.isPresent()) {
            UserSubscriptionConfig config = configOpt.get();
            try {
                if (config.getCategories() != null) {
                    categories = objectMapper.readValue(config.getCategories(), new TypeReference<List<String>>() {});
                }
                if (config.getRegions() != null) {
                    regions = objectMapper.readValue(config.getRegions(), new TypeReference<List<String>>() {});
                }
            } catch (Exception e) {
                log.warn("解析用户订阅配置失败: {}", e.getMessage());
            }
        }

        // ===== 分级分布 =====
        List<Map<String, Object>> gradeDistribution = new ArrayList<>();
        for (Object[] row : leadRepository.countByGrade()) {
            Map<String, Object> map = new HashMap<>();
            map.put("grade", row[0].toString());
            map.put("count", row[1]);
            gradeDistribution.add(map);
        }

        // ===== 近 30 天品类分布 =====
        List<Map<String, Object>> categoryDistribution = leadRepository.countByCategorySince(thirtyDaysAgo).stream()
                .map(row -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("category", row[0] != null ? row[0].toString() : "未分类");
                    map.put("count", row[1]);
                    return map;
                })
                .collect(Collectors.toList());

        // ===== 地区分布 =====
        List<Map<String, Object>> regionDistribution = leadRepository.countByRegionSince(thirtyDaysAgo).stream()
                .map(row -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("region", row[0] != null ? row[0].toString() : "未知");
                    map.put("count", row[1]);
                    return map;
                })
                .collect(Collectors.toList());

        // ===== 需求类型分布 =====
        List<Map<String, Object>> needTypeDistribution = leadRepository.countByNeedTypeSince(thirtyDaysAgo).stream()
                .map(row -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("needType", row[0] != null ? row[0].toString() : "未知");
                    map.put("count", row[1]);
                    return map;
                })
                .collect(Collectors.toList());

        // ===== 近 7 天采集趋势 =====
        List<Map<String, Object>> weeklyTrend = buildWeeklyTrend(sevenDaysAgo);

        // ===== 最近线索（取最近 5 条） =====
        List<Lead> recentLeads = leadRepository.findAllByOrderByPostedAtDesc(PageRequest.of(0, 5)).getContent();
        List<LeadDTO> recentLeadDTOs = recentLeads.stream()
                .map(lead -> toLeadDTO(lead, userId))
                .collect(Collectors.toList());

        return DashboardDTO.builder()
                .todayNewLeads(todayNew)
                .todaySGradeLeads(todaySGrade)
                .totalLeads(totalLeads)
                .unreadCount(unreadCount)
                .dailySampleLimit(dailySampleLimit)
                .todaySampleUsed(todaySampleUsed)
                .trialDaysLeft(trialDaysLeft)
                .isPaid(isPaid)
                .monitoredCategories(categories)
                .monitoredRegions(regions)
                .gradeDistribution(gradeDistribution)
                .categoryDistribution(categoryDistribution)
                .regionDistribution(regionDistribution)
                .needTypeDistribution(needTypeDistribution)
                .weeklyTrend(weeklyTrend)
                .recentLeads(recentLeadDTOs)
                .build();
    }

    /**
     * 构建近 7 天采集趋势（补齐没有数据的日期）
     */
    private List<Map<String, Object>> buildWeeklyTrend(LocalDateTime since) {
        // 从数据库查询有数据的日期
        Map<String, Long> dateCountMap = new HashMap<>();
        for (Object[] row : leadRepository.countByDateSince(since)) {
            String date = row[0] != null ? row[0].toString() : "";
            // 处理 java.sql.Date 或 LocalDate
            if (row[0] instanceof java.sql.Date) {
                date = ((java.sql.Date) row[0]).toLocalDate().toString();
            }
            dateCountMap.put(date, (Long) row[1]);
        }

        // 补齐最近 7 天
        List<Map<String, Object>> trend = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String dateStr = date.format(formatter);
            Map<String, Object> map = new HashMap<>();
            map.put("date", dateStr);
            map.put("count", dateCountMap.getOrDefault(dateStr, 0L));
            trend.add(map);
        }
        return trend;
    }

    private LeadDTO toLeadDTO(Lead lead, Long userId) {
        LeadDTO.LeadDTOBuilder builder = LeadDTO.builder()
                .id(lead.getId())
                .grade(lead.getGrade())
                .gradeLabel(lead.getGrade().getLabel())
                .title(lead.getTitle())
                .body(lead.getBody())
                .sourceUrl(lead.getSourceUrl())
                .author(lead.getAuthor())
                .subreddit(lead.getSubreddit())
                .category(lead.getCategory())
                .orderScale(lead.getOrderScale())
                .needType(lead.getNeedType())
                .region(lead.getRegion())
                .postedAt(lead.getPostedAt())
                .marked(false)
                .isRead(false);

        // 填充用户个性化数据
        userLeadRepository.findByUserIdAndLeadId(userId, lead.getId())
                .ifPresent(ul -> {
                    builder.marked(ul.getMarked());
                    builder.note(ul.getNote());
                    builder.isRead(ul.getRead());
                });

        return builder.build();
    }
}
