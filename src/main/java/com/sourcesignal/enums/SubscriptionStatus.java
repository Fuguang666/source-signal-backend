package com.sourcesignal.enums;

import lombok.Getter;

/**
 * 订阅状态
 */
@Getter
public enum SubscriptionStatus {
    TRIAL("试用中"),
    ACTIVE("已付费"),
    EXPIRED("到期");

    private final String label;

    SubscriptionStatus(String label) {
        this.label = label;
    }
}
