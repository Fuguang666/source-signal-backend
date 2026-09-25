package com.sourcesignal.repository;

import com.sourcesignal.entity.Lead;
import com.sourcesignal.enums.LeadGrade;
import com.sourcesignal.enums.Region;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

    Optional<Lead> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);

    /** 按时间倒序分页查询 */
    Page<Lead> findAllByOrderByPostedAtDesc(Pageable pageable);

    /** 按等级筛选 */
    Page<Lead> findByGradeOrderByPostedAtDesc(LeadGrade grade, Pageable pageable);

    /** 按品类筛选 */
    Page<Lead> findByCategoryOrderByPostedAtDesc(String category, Pageable pageable);

    /** 按地区筛选 */
    Page<Lead> findByRegionOrderByPostedAtDesc(Region region, Pageable pageable);

    /** 多条件筛选 */
    @Query("SELECT l FROM Lead l WHERE " +
           "(:grade IS NULL OR l.grade = :grade) AND " +
           "(:category IS NULL OR l.category = :category) AND " +
           "(:region IS NULL OR l.region = :region) AND " +
           "(:needType IS NULL OR l.needType = :needType) AND " +
           "(:keyword IS NULL OR LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(l.body) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "l.collectedAt >= :since " +
           "ORDER BY l.collectedAt DESC")
    Page<Lead> findByFilters(
            @Param("grade") LeadGrade grade,
            @Param("category") String category,
            @Param("region") Region region,
            @Param("needType") com.sourcesignal.enums.NeedType needType,
            @Param("keyword") String keyword,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    /** 统计今日新增 */
    long countByCollectedAtAfter(LocalDateTime since);

    /** 统计指定等级今日新增 */
    long countByGradeAndCollectedAtAfter(LeadGrade grade, LocalDateTime since);

    /** 近 N 天按品类统计（按采集时间） */
    @Query("SELECT l.category, COUNT(l) FROM Lead l WHERE l.collectedAt >= :since GROUP BY l.category ORDER BY COUNT(l) DESC")
    List<Object[]> countByCategorySince(@Param("since") LocalDateTime since);

    /** 查询未抽检的线索（后台管理用） */
    Page<Lead> findByReviewedFalseOrderByPostedAtDesc(Pageable pageable);

    /** 按等级分组统计 */
    @Query("SELECT l.grade, COUNT(l) FROM Lead l GROUP BY l.grade ORDER BY l.grade")
    List<Object[]> countByGrade();

    /** 近 N 天按地区分组统计（按采集时间） */
    @Query("SELECT l.region, COUNT(l) FROM Lead l WHERE l.collectedAt >= :since GROUP BY l.region ORDER BY COUNT(l) DESC")
    List<Object[]> countByRegionSince(@Param("since") LocalDateTime since);

    /** 近 N 天按需求类型分组统计（按采集时间） */
    @Query("SELECT l.needType, COUNT(l) FROM Lead l WHERE l.collectedAt >= :since GROUP BY l.needType ORDER BY COUNT(l) DESC")
    List<Object[]> countByNeedTypeSince(@Param("since") LocalDateTime since);

    /** 近 N 天按日期分组统计（采集趋势） */
    @Query("SELECT FUNCTION('DATE', l.collectedAt), COUNT(l) FROM Lead l WHERE l.collectedAt >= :since GROUP BY FUNCTION('DATE', l.collectedAt) ORDER BY FUNCTION('DATE', l.collectedAt)")
    List<Object[]> countByDateSince(@Param("since") LocalDateTime since);
}
