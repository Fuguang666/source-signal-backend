package com.sourcesignal.enums;

import lombok.Getter;

/**
 * 需求类型
 */
@Getter
public enum NeedType {
    FULL_AGENT("找全链路采购代理"),
    FACTORY("找工厂代工"),
    QC("找质检服务"),
    LOGISTICS("找物流清关"),
    SUPPLY_CHAIN("找供应链合作"),
    CONSIDERING_AGENT("考虑找代理"),
    BEGINNER("新手入门咨询");

    private final String label;

    NeedType(String label) {
        this.label = label;
    }
}
