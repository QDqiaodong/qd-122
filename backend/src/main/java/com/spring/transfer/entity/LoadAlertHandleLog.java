package com.spring.transfer.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * 负载告警处置记录：对告警事件的每次处置动作留痕
 * （确认责任人与处置计划 / 追加备注 / 关闭 / 系统自动关闭）。
 */
@Data
@Entity
@Table(name = "load_alert_handle_log")
public class LoadAlertHandleLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    /**
     * 操作类型：CONFIRM-确认处置 PLAN-更新处置计划 REMARK-追加备注
     * RESOLVE-手动关闭 AUTO_RESOLVE-自动关闭 REOPEN-重新激活
     */
    @Column(name = "action", nullable = false, length = 16)
    private String action;

    @Column(name = "operator", nullable = false, length = 32)
    private String operator;

    @CreationTimestamp
    @Column(name = "operate_time", nullable = false)
    private LocalDateTime operateTime;

    /** 操作后事件状态：PENDING / PROCESSING / RESOLVED */
    @Column(name = "result_status", nullable = false, length = 16)
    private String resultStatus;

    @Column(name = "detail", length = 512)
    private String detail;

    public LoadAlertHandleLog() {
    }

    public LoadAlertHandleLog(Long eventId, String action, String operator,
                              String resultStatus, String detail) {
        this.eventId = eventId;
        this.action = action;
        this.operator = operator;
        this.resultStatus = resultStatus;
        this.detail = detail;
    }
}
