package com.spring.transfer.common;

/**
 * 电表抄录班次。
 * DAY-白班 NIGHT-夜班；每条产线每个班次当天只能留下一条抄表记录。
 */
public enum MeterShift {
    DAY("白班"),
    NIGHT("夜班");

    private final String label;

    MeterShift(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
