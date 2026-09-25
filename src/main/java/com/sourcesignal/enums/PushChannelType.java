package com.sourcesignal.enums;

import lombok.Getter;

/**
 * 推送渠道类型
 */
@Getter
public enum PushChannelType {
    IN_APP("站内通知", true),
    EMAIL("邮件推送", true),
    TELEGRAM("Telegram Bot", false),
    WECOM("企业微信", false),
    DINGTALK("钉钉", false);

    private final String label;
    private final boolean trialAvailable;  // 试用版是否可用

    PushChannelType(String label, boolean trialAvailable) {
        this.label = label;
        this.trialAvailable = trialAvailable;
    }
}
