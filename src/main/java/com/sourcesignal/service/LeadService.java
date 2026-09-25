package com.sourcesignal.service;

import com.sourcesignal.common.BusinessException;
import com.sourcesignal.common.PageResult;
import com.sourcesignal.common.ResultCode;
import com.sourcesignal.dto.LeadDTO;
import com.sourcesignal.dto.LeadQueryRequest;
import com.sourcesignal.entity.Lead;
import com.sourcesignal.entity.UserLead;
import com.sourcesignal.repository.LeadRepository;
import com.sourcesignal.repository.UserLeadRepository;
import com.sourcesignal.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    private final LeadRepository leadRepository;
    private final UserLeadRepository userLeadRepository;
    private final CurrentUser currentUser;
    private final PermissionService permissionService;

    /**
     * 分页查询线索列表
     */
    public PageResult<LeadDTO> queryLeads(LeadQueryRequest request) {
        Long userId = currentUser.getCurrentUserId();
        // 校验订阅状态
        permissionService.requireActiveSubscription();

        int page = Math.max(1, request.getPage()) - 1;
        int size = Math.min(100, Math.max(1, request.getSize()));
        Pageable pageable = PageRequest.of(page, size);

        // 根据订阅状态确定历史可见范围（试用版 7 天，付费版 30 天）
        LocalDateTime since = permissionService.getHistorySince();

        Page<Lead> leadPage = leadRepository.findByFilters(
                request.getGrade(),
                request.getCategory(),
                request.getRegion(),
                request.getNeedType(),
                request.getKeyword(),
                since,
                pageable
        );

        List<LeadDTO> dtoList = leadPage.getContent().stream()
                .map(lead -> toLeadDTO(lead, userId))
                .collect(Collectors.toList());

        return PageResult.of(dtoList, leadPage.getTotalElements(), request.getPage(), size);
    }

    /**
     * 获取线索详情（自动标记为已读）
     */
    @Transactional
    public LeadDTO getLeadDetail(Long leadId) {
        Long userId = currentUser.getCurrentUserId();
        permissionService.requireActiveSubscription();
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new BusinessException(ResultCode.LEAD_NOT_FOUND));

        // 自动标记为已读
        UserLead userLead = userLeadRepository.findByUserIdAndLeadId(userId, leadId)
                .orElseGet(() -> UserLead.builder()
                        .userId(userId)
                        .leadId(leadId)
                        .marked(false)
                        .read(false)
                        .build());
        if (!Boolean.TRUE.equals(userLead.getRead())) {
            userLead.setRead(true);
            userLeadRepository.save(userLead);
        }

        return toLeadDTO(lead, userId);
    }

    /**
     * 标记/取消标记线索
     */
    @Transactional
    public LeadDTO toggleMark(Long leadId) {
        Long userId = currentUser.getCurrentUserId();
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new BusinessException(ResultCode.LEAD_NOT_FOUND));

        UserLead userLead = userLeadRepository.findByUserIdAndLeadId(userId, leadId)
                .orElseGet(() -> UserLead.builder()
                        .userId(userId)
                        .leadId(leadId)
                        .marked(false)
                        .read(true)
                        .build());

        userLead.setMarked(!Boolean.TRUE.equals(userLead.getMarked()));
        userLeadRepository.save(userLead);

        return toLeadDTO(lead, userId);
    }

    /**
     * 保存跟进备注
     */
    @Transactional
    public LeadDTO saveNote(Long leadId, String note) {
        Long userId = currentUser.getCurrentUserId();
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new BusinessException(ResultCode.LEAD_NOT_FOUND));

        UserLead userLead = userLeadRepository.findByUserIdAndLeadId(userId, leadId)
                .orElseGet(() -> UserLead.builder()
                        .userId(userId)
                        .leadId(leadId)
                        .marked(false)
                        .read(true)
                        .build());

        userLead.setNote(note);
        userLeadRepository.save(userLead);

        return toLeadDTO(lead, userId);
    }

    /**
     * 获取用户标记的线索列表
     */
    public List<LeadDTO> getMarkedLeads() {
        Long userId = currentUser.getCurrentUserId();
        List<UserLead> markedList = userLeadRepository.findByUserIdAndMarkedTrue(userId);
        return markedList.stream()
                .map(ul -> leadRepository.findById(ul.getLeadId()).orElse(null))
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
