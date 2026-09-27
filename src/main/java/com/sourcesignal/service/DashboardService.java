package com.sourcesignal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sourcesignal.dto.DashboardDTO;
import com.sourcesignal.dto.LeadDTO;
import com.sourcesignal.entity.Lead;
import com.sourcesignal.entity.Subscription;
import com.sourcesignal.entity.UserLead;
import com.sourcesignal.entity.UserSubscriptionConfig;
import com.sourcesignal.enums.LeadGrade;
import com.sourcesignal.enums.PlanType;
import com.sourcesignal.enums.SubscriptionStatus;
import com.sourcesignal.mapper.LeadMapper;
import com.sourcesignal.mapper.SubscriptionMapper;
import com.sourcesignal.mapper.UserLeadMapper;
import com.sourcesignal.mapper.UserSubscriptionConfigMapper;
import com.sourcesignal.security.CurrentUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    private final LeadMapper leadMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final UserSubscriptionConfigMapper configMapper;
    private final UserLeadMapper userLeadMapper;
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
        long todayNew = leadMapper.countCollectedAfter(todayStart);
        long todaySGrade = leadMapper.countByGradeAndCollectedAfter(LeadGrade.S.name(), todayStart);
        long totalLeads = leadMapper.selectCount(null);
        long readCount = userLeadMapper.countReadByUserId(userId);
        long unreadCount = Math.max(0, totalLeads - readCount);

        // ===== 订阅信息 =====
        Subscription subscription = subscriptionMapper.selectOne(new LambdaQueryWrapper<Subscription>()
                .eq(Subscription::getUserId, userId));
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
        UserSubscriptionConfig config = configMapper.selectOne(new LambdaQueryWrapper<UserSubscriptionConfig>()
                .eq(UserSubscriptionConfig::getUserId, userId));
        if (config != null) {
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
        for (Map<String, Object> row : leadMapper.countByGradeGroup()) {
            Map<String, Object> map = new HashMap<>();
            map.put("grade", row.get("grade") != null ? row.get("grade").toString() : "未知");
            map.put("count", row.get("cnt"));
            gradeDistribution.add(map);
        }

        // ===== 近 30 天品类分布 =====
        List<Map<String, Object>> categoryDistribution = leadMapper.countByCategorySince(thirtyDaysAgo).stream()
                .map(row -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("category", row.get("category") != null ? row.get("category").toString() : "其他");
                    map.put("count", row.get("cnt"));
                    return map;
                })
                .collect(Collectors.toList());

        // ===== 地区分布 =====
        List<Map<String, Object>> regionDistribution = leadMapper.countByRegionSince(thirtyDaysAgo).stream()
                .map(row -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("region", row.get("region") != null ? row.get("region").toString() : "未知");
                    map.put("count", row.get("cnt"));
                    return map;
                })
                .collect(Collectors.toList());

        // ===== 需求类型分布 =====
        List<Map<String, Object>> needTypeDistribution = leadMapper.countByNeedTypeSince(thirtyDaysAgo).stream()
                .map(row -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("needType", row.get("need_type") != null ? row.get("need_type").toString() : "未知");
                    map.put("count", row.get("cnt"));
                    return map;
                })
                .collect(Collectors.toList());

        // ===== 近 7 天采集趋势 =====
        List<Map<String, Object>> weeklyTrend = buildWeeklyTrend(sevenDaysAgo);

        // ===== 最近线索（取最近 5 条，按采集时间倒序） =====
        List<Lead> recentLeads = leadMapper.selectList(new LambdaQueryWrapper<Lead>()
                .orderByDesc(Lead::getCollectedAt)
                .last("LIMIT 5"));
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
        Map<String, Long> dateCountMap = new HashMap<>();
        for (Map<String, Object> row : leadMapper.countByDateSince(since)) {
            Object dtObj = row.get("dt");
            String date = dtObj != null ? dtObj.toString() : "";
            if (dtObj instanceof java.sql.Date) {
                date = ((java.sql.Date) dtObj).toLocalDate().toString();
            }
            Object cntObj = row.get("cnt");
            long cnt = cntObj instanceof Number ? ((Number) cntObj).longValue() : 0L;
            dateCountMap.put(date, cnt);
        }

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
                .pushedAt(lead.getCollectedAt())
                .marked(false)
                .isRead(false);

        UserLead userLead = userLeadMapper.selectOne(new LambdaQueryWrapper<UserLead>()
                .eq(UserLead::getUserId, userId)
                .eq(UserLead::getLeadId, lead.getId()));
        if (userLead != null) {
            builder.marked(userLead.getMarked());
            builder.note(userLead.getNote());
            builder.isRead(userLead.getIsRead());
        }

        return builder.build();
    }
}
