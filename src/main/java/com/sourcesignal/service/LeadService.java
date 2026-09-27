package com.sourcesignal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sourcesignal.common.BusinessException;
import com.sourcesignal.common.PageResult;
import com.sourcesignal.common.ResultCode;
import com.sourcesignal.dto.LeadDTO;
import com.sourcesignal.dto.LeadQueryRequest;
import com.sourcesignal.entity.Lead;
import com.sourcesignal.entity.UserLead;
import com.sourcesignal.mapper.LeadMapper;
import com.sourcesignal.mapper.UserLeadMapper;
import com.sourcesignal.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 线索服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadMapper leadMapper;
    private final UserLeadMapper userLeadMapper;
    private final CurrentUser currentUser;
    private final PermissionService permissionService;

    /**
     * 分页查询线索列表
     */
    public PageResult<LeadDTO> queryLeads(LeadQueryRequest request) {
        Long userId = currentUser.getCurrentUserId();
        permissionService.requireActiveSubscription();

        int pageNum = Math.max(1, request.getPage());
        int size = Math.min(100, Math.max(1, request.getSize()));
        LocalDateTime since = permissionService.getHistorySince();

        // 枚举转字符串（MyBatis-Plus 自定义 SQL 用字符串）
        String gradeStr = request.getGrade() != null ? request.getGrade().name() : null;
        String regionStr = request.getRegion() != null ? request.getRegion().name() : null;
        String needTypeStr = request.getNeedType() != null ? request.getNeedType().name() : null;

        Page<Lead> page = new Page<>(pageNum, size);
        IPage<Lead> leadPage = leadMapper.selectPageByFilters(page,
                userId, gradeStr, request.getCategory(), regionStr, needTypeStr,
                request.getKeyword(), request.getMarked(), since);

        List<LeadDTO> dtoList = leadPage.getRecords().stream()
                .map(lead -> toLeadDTO(lead, userId))
                .collect(Collectors.toList());

        return PageResult.of(dtoList, leadPage.getTotal(), pageNum, size);
    }

    /**
     * 获取线索详情（自动标记为已读）
     */
    @Transactional
    public LeadDTO getLeadDetail(Long leadId) {
        Long userId = currentUser.getCurrentUserId();
        permissionService.requireActiveSubscription();
        Lead lead = leadMapper.selectById(leadId);
        if (lead == null) {
            throw new BusinessException(ResultCode.LEAD_NOT_FOUND);
        }

        // 自动标记为已读
        UserLead userLead = userLeadMapper.selectOne(new LambdaQueryWrapper<UserLead>()
                .eq(UserLead::getUserId, userId)
                .eq(UserLead::getLeadId, leadId));
        if (userLead == null) {
            userLead = UserLead.builder()
                    .userId(userId)
                    .leadId(leadId)
                    .marked(false)
                    .isRead(false)
                    .build();
        }
        if (!Boolean.TRUE.equals(userLead.getIsRead())) {
            userLead.setIsRead(true);
            if (userLead.getId() == null) {
                userLeadMapper.insert(userLead);
            } else {
                userLeadMapper.updateById(userLead);
            }
        }

        return toLeadDTO(lead, userId);
    }

    /**
     * 标记/取消标记线索
     */
    @Transactional
    public LeadDTO toggleMark(Long leadId) {
        Long userId = currentUser.getCurrentUserId();
        Lead lead = leadMapper.selectById(leadId);
        if (lead == null) {
            throw new BusinessException(ResultCode.LEAD_NOT_FOUND);
        }

        UserLead userLead = userLeadMapper.selectOne(new LambdaQueryWrapper<UserLead>()
                .eq(UserLead::getUserId, userId)
                .eq(UserLead::getLeadId, leadId));
        if (userLead == null) {
            userLead = UserLead.builder()
                    .userId(userId)
                    .leadId(leadId)
                    .marked(false)
                    .isRead(true)
                    .build();
            userLeadMapper.insert(userLead);
        }

        userLead.setMarked(!Boolean.TRUE.equals(userLead.getMarked()));
        userLeadMapper.updateById(userLead);

        return toLeadDTO(lead, userId);
    }

    /**
     * 保存跟进备注
     */
    @Transactional
    public LeadDTO saveNote(Long leadId, String note) {
        Long userId = currentUser.getCurrentUserId();
        Lead lead = leadMapper.selectById(leadId);
        if (lead == null) {
            throw new BusinessException(ResultCode.LEAD_NOT_FOUND);
        }

        UserLead userLead = userLeadMapper.selectOne(new LambdaQueryWrapper<UserLead>()
                .eq(UserLead::getUserId, userId)
                .eq(UserLead::getLeadId, leadId));
        if (userLead == null) {
            userLead = UserLead.builder()
                    .userId(userId)
                    .leadId(leadId)
                    .marked(false)
                    .isRead(true)
                    .build();
            userLeadMapper.insert(userLead);
        }

        userLead.setNote(note);
        userLeadMapper.updateById(userLead);

        return toLeadDTO(lead, userId);
    }

    /**
     * 获取用户标记的线索列表
     */
    public List<LeadDTO> getMarkedLeads() {
        Long userId = currentUser.getCurrentUserId();
        List<UserLead> markedList = userLeadMapper.selectList(new LambdaQueryWrapper<UserLead>()
                .eq(UserLead::getUserId, userId)
                .eq(UserLead::getMarked, true));
        return markedList.stream()
                .map(ul -> leadMapper.selectById(ul.getLeadId()))
                .filter(java.util.Objects::nonNull)
                .map(lead -> toLeadDTO(lead, userId))
                .collect(Collectors.toList());
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

        // 填充用户个性化数据
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
