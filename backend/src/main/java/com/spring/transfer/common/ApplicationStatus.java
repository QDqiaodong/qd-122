package com.spring.transfer.common;

/**
 * 划转申请单状态
 */
public enum ApplicationStatus {
    /** 待审批 */
    PENDING,
    /** 全部通过 */
    APPROVED,
    /** 全部驳回 */
    REJECTED,
    /** 部分处理（部分通过/部分驳回/部分待审） */
    PARTIAL
}
