package com.spring.transfer.common;

/**
 * 夜班承载复核单状态。
 * PENDING-待确认（交班调度员已签发，接班调度员尚未确认）
 * CONFIRMED-已确认（接班调度员已填写跟进说明并确认，数字随即锁定不可再改）
 */
public enum ReviewStatus {
    PENDING,
    CONFIRMED
}
