package com.spring.transfer.common;

/**
 * 弹簧封存状态
 */
public enum SealStatus {
    /** 正常（未封存） */
    NONE,
    /** 封存中（抽检不合格/待复测，禁止划转与调拨模拟） */
    SEALED
}
