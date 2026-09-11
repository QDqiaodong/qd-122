package com.spring.transfer.entity;

import com.spring.transfer.common.AlertStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/**
 * 产线负载告警事件：当产线出现数量超阈值（超载）、弹力系数越界或
 * 近 7 天持续净流入触发预警时生成。
 *
 * 同一产线同一轮负载异常期间只存在一条未关闭事件（active_line_id 唯一）；
 * 异常恢复（负载转正常）后事件关闭，再次触发时生成新事件。
 */
@Data
@Entity
@Table(name = "load_alert_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_active_line", columnNames = "active_line_id"))
public class LoadAlertEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_no", nullable = false, unique = true, length = 32)
    private String eventNo;

    @Column(name = "line_id", nullable = false)
    private Long lineId;

    @Column(name = "line_code", nullable = false, length = 32)
    private String lineCode;

    @Column(name = "line_name", nullable = false, length = 64)
    private String lineName;

    /** 触发时的告警级别：WARNING-预警 / OVERLOAD-超载 */
    @Column(name = "alert_level", nullable = false, length = 16)
    private String alertLevel;

    /** 触发时的告警快照（LineLoadStats JSON），事件期间不变，刷新后仍可追溯 */
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    /** 处置状态：PENDING / PROCESSING / RESOLVED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AlertStatus status = AlertStatus.PENDING;

    /** 调度员确认的责任人 */
    @Column(name = "responsible_person", length = 32)
    private String responsiblePerson;

    /** 处置计划 */
    @Column(name = "handle_plan", length = 512)
    private String handlePlan;

    /** 备注 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 关闭方式：MANUAL-调度员手动关闭 AUTO-负载恢复正常系统自动关闭 */
    @Column(name = "close_type", length = 16)
    private String closeType;

    /** 关闭说明（关闭时的处理备注） */
    @Column(name = "close_remark", length = 512)
    private String closeRemark;

    /** 关闭操作人（系统自动关闭时为 SYSTEM） */
    @Column(name = "closed_by", length = 32)
    private String closedBy;

    @Column(name = "trigger_time", nullable = false)
    private LocalDateTime triggerTime;

    /** 首次确认责任人/处置计划的时间 */
    @Column(name = "confirm_time")
    private LocalDateTime confirmTime;

    @Column(name = "close_time")
    private LocalDateTime closeTime;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /**
     * 未关闭事件标记：未关闭时等于 lineId（由该列唯一约束保证同一产线同时只有一条
     * 未关闭事件）；关闭后置空，且置空后唯一索引不再约束历史行，允许下一轮异常再建新事件。
     */
    @Column(name = "active_line_id")
    private Long activeLineId;

    /**
     * 手动关闭抑制标记：调度员在产线仍异常时手动关闭事件则置 true，同一轮持续异常期间
     * 不再重复生成事件；负载恢复正常后清除，之后再次触发会生成新事件。
     */
    @Column(name = "manual_close_active", nullable = false)
    private boolean manualCloseActive = false;
}
