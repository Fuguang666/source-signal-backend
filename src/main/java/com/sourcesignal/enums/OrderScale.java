package com.sourcesignal.enums;

import lombok.Getter;

/**
 * 订单量级
 */
@Getter
public enum OrderScale {
    SMALL("小单 · 月采<1万美金"),
    MEDIUM("中单 · 1–10万美金"),
    LARGE("大单 · >10万美金"),
    UNKNOWN("—");

    private final String label;

    OrderScale(String label) {
        this.label = label;
    }
}
