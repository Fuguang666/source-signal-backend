package com.sourcesignal.enums;

import lombok.Getter;

/**
 * 推送频率
 */
@Getter
public enum PushFrequency {
    REALTIME("实时推送", "新线索即刻到达"),
    DAILY("每日汇总", "每天 09:00"),
    WEEKLY("每周汇总", "周一 09:00");

    private final String label;
    private final String description;

    PushFrequency(String label, String description) {
        this.label = label;
        this.description = description;
    }
}
