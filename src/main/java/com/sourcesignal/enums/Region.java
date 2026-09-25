package com.sourcesignal.enums;

import lombok.Getter;

/**
 * 目标地区
 */
@Getter
public enum Region {
    NORTH_AMERICA("北美"),
    EUROPE("欧洲"),
    SOUTHEAST_ASIA("东南亚"),
    AUSTRALIA("澳洲"),
    OTHER("其他");

    private final String label;

    Region(String label) {
        this.label = label;
    }
}
