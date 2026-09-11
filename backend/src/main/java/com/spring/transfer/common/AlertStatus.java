package com.spring.transfer.common;

/**
 * 负载告警处置状态：
 * PENDING-待处理（新生成的告警事件）
 * PROCESSING-处置中（已确认责任人并填写处置计划）
 * RESOLVED-已关闭（调度员标记处理完成，或负载恢复正常由系统自动关闭）
 */
public enum AlertStatus {
    PENDING,
    PROCESSING,
    RESOLVED
}
