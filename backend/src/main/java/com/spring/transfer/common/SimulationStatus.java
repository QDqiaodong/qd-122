package com.spring.transfer.common;

/**
 * 调拨模拟方案状态
 */
public enum SimulationStatus {
    /** 已保存（草稿，可采用或作废） */
    DRAFT,
    /** 已采用（已生成待审批划转申请） */
    ADOPTED,
    /** 已作废 */
    DISCARDED
}
