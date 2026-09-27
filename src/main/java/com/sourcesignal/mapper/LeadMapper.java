package com.sourcesignal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sourcesignal.entity.Lead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 采购线索 Mapper
 */
@Mapper
public interface LeadMapper extends BaseMapper<Lead> {

    /**
     * 多条件筛选分页查询
     * 所有参数均可为 null（null 表示不限制该条件）
     * marked=true: 只看已标记; marked=false: 只看未标记（含无 UserLead 记录）
     * unread=true: 只看未读（含无 UserLead 记录）; unread=false: 只看已读
     */
    @Select("<script>" +
            "SELECT l.* FROM procurement_lead l " +
            "LEFT JOIN user_lead ul ON ul.lead_id = l.id AND ul.user_id = #{userId} " +
            "WHERE 1=1 " +
            "<if test='grade != null'> AND l.grade = #{grade} </if>" +
            "<if test='category != null'> AND l.category = #{category} </if>" +
            "<if test='region != null'> AND l.region = #{region} </if>" +
            "<if test='needType != null'> AND l.need_type = #{needType} </if>" +
            "<if test='keyword != null'> AND (LOWER(l.title) LIKE CONCAT('%', LOWER(#{keyword}), '%') OR LOWER(l.body) LIKE CONCAT('%', LOWER(#{keyword}), '%')) </if>" +
            "<if test='marked != null and marked'> AND ul.marked = 1 </if>" +
            "<if test='marked != null and !marked'> AND (ul.marked = 0 OR ul.id IS NULL) </if>" +
            "<if test='unread != null and unread'> AND (ul.is_read = 0 OR ul.id IS NULL) </if>" +
            "<if test='unread != null and !unread'> AND ul.is_read = 1 </if>" +
            "AND l.collected_at >= #{since} " +
            "ORDER BY l.collected_at DESC" +
            "</script>")
    IPage<Lead> selectPageByFilters(IPage<Lead> page,
                                     @Param("userId") Long userId,
                                     @Param("grade") String grade,
                                     @Param("category") String category,
                                     @Param("region") String region,
                                     @Param("needType") String needType,
                                     @Param("keyword") String keyword,
                                     @Param("marked") Boolean marked,
                                     @Param("unread") Boolean unread,
                                     @Param("since") LocalDateTime since);

    /** 统计用户未读线索数（is_read=0 或无 UserLead 记录） */
    @Select("SELECT COUNT(*) FROM procurement_lead l " +
            "LEFT JOIN user_lead ul ON ul.lead_id = l.id AND ul.user_id = #{userId} " +
            "WHERE (ul.is_read = 0 OR ul.id IS NULL) " +
            "AND l.collected_at >= #{since}")
    long countUnread(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /** 统计指定时间之后采集的线索数 */
    @Select("SELECT COUNT(*) FROM procurement_lead WHERE collected_at >= #{since}")
    long countCollectedAfter(@Param("since") LocalDateTime since);

    /** 统计指定等级且指定时间之后采集的线索数 */
    @Select("SELECT COUNT(*) FROM procurement_lead WHERE grade = #{grade} AND collected_at >= #{since}")
    long countByGradeAndCollectedAfter(@Param("grade") String grade,
                                        @Param("since") LocalDateTime since);

    /** 按等级分组统计，返回 [{grade, cnt}, ...] */
    @Select("SELECT grade, COUNT(*) as cnt FROM procurement_lead GROUP BY grade ORDER BY grade")
    List<Map<String, Object>> countByGradeGroup();

    /** 近 N 天按品类统计，返回 [{category, cnt}, ...] */
    @Select("SELECT category, COUNT(*) as cnt FROM procurement_lead WHERE collected_at >= #{since} GROUP BY category ORDER BY cnt DESC")
    List<Map<String, Object>> countByCategorySince(@Param("since") LocalDateTime since);

    /** 近 N 天按地区分组统计，返回 [{region, cnt}, ...] */
    @Select("SELECT region, COUNT(*) as cnt FROM procurement_lead WHERE collected_at >= #{since} GROUP BY region ORDER BY cnt DESC")
    List<Map<String, Object>> countByRegionSince(@Param("since") LocalDateTime since);

    /** 近 N 天按需求类型分组统计，返回 [{need_type, cnt}, ...] */
    @Select("SELECT need_type, COUNT(*) as cnt FROM procurement_lead WHERE collected_at >= #{since} GROUP BY need_type ORDER BY cnt DESC")
    List<Map<String, Object>> countByNeedTypeSince(@Param("since") LocalDateTime since);

    /** 近 N 天按日期分组统计，返回 [{dt, cnt}, ...] */
    @Select("SELECT DATE(collected_at) as dt, COUNT(*) as cnt FROM procurement_lead WHERE collected_at >= #{since} GROUP BY DATE(collected_at) ORDER BY dt")
    List<Map<String, Object>> countByDateSince(@Param("since") LocalDateTime since);
}
