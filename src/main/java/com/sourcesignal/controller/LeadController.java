package com.sourcesignal.controller;

import com.sourcesignal.common.PageResult;
import com.sourcesignal.common.Result;
import com.sourcesignal.dto.LeadDTO;
import com.sourcesignal.dto.LeadQueryRequest;
import com.sourcesignal.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 线索库控制器
 */
@Tag(name = "线索库", description = "线索列表、详情、标记、备注")
@RestController
@RequestMapping("/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @Operation(summary = "分页查询线索", description = "支持按等级、品类、地区、需求类型、关键词筛选")
    @GetMapping
    public Result<PageResult<LeadDTO>> queryLeads(LeadQueryRequest request) {
        return Result.success(leadService.queryLeads(request));
    }

    @Operation(summary = "获取线索详情")
    @GetMapping("/{id}")
    public Result<LeadDTO> getLeadDetail(@PathVariable Long id) {
        return Result.success(leadService.getLeadDetail(id));
    }

    @Operation(summary = "标记/取消标记线索")
    @PostMapping("/{id}/mark")
    public Result<LeadDTO> toggleMark(@PathVariable Long id) {
        return Result.success(leadService.toggleMark(id));
    }

    @Operation(summary = "保存跟进备注")
    @PostMapping("/{id}/note")
    public Result<LeadDTO> saveNote(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.success(leadService.saveNote(id, body.get("note")));
    }

    @Operation(summary = "获取已标记线索列表")
    @GetMapping("/marked")
    public Result<List<LeadDTO>> getMarkedLeads() {
        return Result.success(leadService.getMarkedLeads());
    }
}
