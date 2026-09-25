package com.sourcesignal.enums;

import lombok.Getter;

/**
 * 订阅套餐类型
 */
@Getter
public enum PlanType {
    TRIAL("试用版", 0.0),
    PAID("付费版", 129.0);

    private final String label;
    private final Double monthlyPrice;

    PlanType(String label, Double monthlyPrice) {
        this.label = label;
        this.monthlyPrice = monthlyPrice;
    }
}
