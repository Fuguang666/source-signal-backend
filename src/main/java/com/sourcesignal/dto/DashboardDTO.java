package com.sourcesignal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {
    /** 今日新信号数 */
    private Long todayNewLeads;
    /** 今日 S 级高意向数 */
    private Long todaySGradeLeads;
    /** 今日 A 级中意向数 */
    private Long todayAGradeLeads;
    /** 线索总数 */
    private Long totalLeads;
    /** 未读线索数 */
    private Long unreadCount;
    /** 试用版每日样例数 */
    private Integer dailySampleLimit;
    /** 今日已推送样例数 */
    private Integer todaySampleUsed;
    /** 剩余试用天数 */
    private Long trialDaysLeft;
    /** 是否付费版 */
    private Boolean isPaid;
    /** 监控品类 */
    private List<String> monitoredCategories;
    /** 监控地区 */
    private List<String> monitoredRegions;
    /** 分级分布（S/A/B） */
    private List<Map<String, Object>> gradeDistribution;
    /** 近 30 天品类分布 */
    private List<Map<String, Object>> categoryDistribution;
    /** 地区分布 */
    private List<Map<String, Object>> regionDistribution;
    /** 需求类型分布 */
    private List<Map<String, Object>> needTypeDistribution;
    /** 近 7 天采集趋势 */
    private List<Map<String, Object>> weeklyTrend;
    /** 最近推送线索 */
    private List<LeadDTO> recentLeads;
}
