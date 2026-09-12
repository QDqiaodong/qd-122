package com.spring.transfer.entity;

import com.spring.transfer.common.ReviewStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 夜班承载复核单：调度员下班前按产线签发，记录交班时点该产线的承载复核快照。
 *
 * 快照数字（当前归属弹簧数、是否压到日承载、系数越界条数、待批划转）在签发时计算并固化，
 * 接班调度员确认后整单锁定，数字不可再改；状态持久化，刷新页面后保持。
 */
@Data
@Entity
@Table(name = "night_load_review",
        uniqueConstraints = @UniqueConstraint(name = "uk_review_no", columnNames = "review_no"))
public class NightLoadReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_no", nullable = false, unique = true, length = 32)
    private String reviewNo;

    /** 复核所属夜班日期（按签发时点 Asia/Shanghai 落表） */
    @Column(name = "review_date", nullable = false)
    private LocalDate reviewDate;

    @Column(name = "line_id", nullable = false)
    private Long lineId;

    /** 产线编码/名称快照，签发后即使产线档案调整也不影响复核单展示 */
    @Column(name = "line_code", nullable = false, length = 32)
    private String lineCode;

    @Column(name = "line_name", nullable = false, length = 64)
    private String lineName;

    /** 签发时当前归属弹簧数（快照） */
    @Column(name = "spring_count", nullable = false)
    private Integer springCount;

    /** 签发时日承载阈值（快照），null 表示当时尚未配置 */
    @Column(name = "daily_capacity_threshold")
    private Integer dailyCapacityThreshold;

    /** 签发时承载率（快照，百分比，保留两位） */
    @Column(name = "load_rate", precision = 10, scale = 2)
    private java.math.BigDecimal loadRate;

    /** 是否压到日承载：当前归属数 ≥ 日承载阈值（含已达/超载） */
    @Column(name = "capacity_reached", nullable = false)
    private boolean capacityReached;

    /** 是否已超过日承载：当前归属数 > 日承载阈值 */
    @Column(name = "over_capacity", nullable = false)
    private boolean overCapacity;

    /** 系数越界条数：签发时归属弹簧中弹力系数超出该产线适用区间的件数（快照） */
    @Column(name = "out_of_range_count", nullable = false)
    private Integer outOfRangeCount;

    /** 次日必须跟进的待批划转明细条数（流向该产线的待审批申请明细，快照） */
    @Column(name = "pending_transfer_count", nullable = false)
    private Integer pendingTransferCount;

    /** 次日必须跟进的待批划转申请单数（去重，快照） */
    @Column(name = "pending_application_count", nullable = false)
    private Integer pendingApplicationCount;

    /** 待批划转明细快照（JSON：申请单号/弹簧编号/申请人等），便于接班人逐条跟进 */
    @Column(name = "pending_transfer_snapshot", columnDefinition = "TEXT")
    private String pendingTransferSnapshot;

    /** 交班备注（签发人可选填） */
    @Column(name = "handover_remark", length = 512)
    private String handoverRemark;

    /** 签发人（交班调度员） */
    @Column(name = "operator", nullable = false, length = 32)
    private String operator;

    /** 签发时间 */
    @Column(name = "issue_time", nullable = false)
    private LocalDateTime issueTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReviewStatus status = ReviewStatus.PENDING;

    /** 跟进说明（接班调度员确认时必填），确认后随数字一并锁定 */
    @Column(name = "follow_up_note", length = 512)
    private String followUpNote;

    /** 确认人（接班调度员） */
    @Column(name = "confirmer", length = 32)
    private String confirmer;

    /** 确认时间 */
    @Column(name = "confirm_time")
    private LocalDateTime confirmTime;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}
