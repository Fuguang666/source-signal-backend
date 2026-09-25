package com.sourcesignal.enums;

import lombok.Getter;

/**
 * 线索意向等级
 * S = 高意向（明确求购+具体品类量级+询价）
 * A = 中意向（有明确采购计划，提推荐）
 * B = 低意向（潜在需求，未明确）
 */
@Getter
public enum LeadGrade {
    S("S 级 · 高意向", "明确求购，给出具体品类与量级，直接询价"),
    A("A 级 · 中意向", "有明确采购计划，询问供应商推荐"),
    B("B 级 · 低意向", "讨论采购痛点，存在潜在需求");

    private final String label;
    private final String description;

    LeadGrade(String label, String description) {
        this.label = label;
        this.description = description;
    }
}
