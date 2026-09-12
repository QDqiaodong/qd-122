package com.spring.transfer.entity;

import com.spring.transfer.common.ApplicationStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "transfer_application")
public class TransferApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_no", nullable = false, unique = true, length = 32)
    private String applicationNo;

    @Column(name = "applicant", nullable = false, length = 32)
    private String applicant;

    @CreationTimestamp
    @Column(name = "apply_time", nullable = false, updatable = false)
    private LocalDateTime applyTime;

    @Column(name = "to_line_id", nullable = false)
    private Long toLineId;

    @Column(name = "to_line_name", nullable = false, length = 64)
    private String toLineName;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    /** 加急标记：调度员对待审批申请标记加急后审批台优先展示；结案（全部通过/驳回）后自动解除 */
    @Column(name = "urgent", nullable = false)
    private boolean urgent = false;

    /** 加急原因（标记加急时必填） */
    @Column(name = "urgent_reason", length = 255)
    private String urgentReason;

    /** 加急操作人（调度员） */
    @Column(name = "urgent_operator", length = 32)
    private String urgentOperator;

    /** 加急时间 */
    @Column(name = "urgent_time")
    private LocalDateTime urgentTime;

    @Transient
    private Integer totalCount;

    @Transient
    private Integer pendingCount;

    @Transient
    private Integer approvedCount;

    @Transient
    private Integer rejectedCount;

    /** 目标产线当前是否停台（列表/详情实时挂接，不持久化） */
    @Transient
    private Boolean toLineHalted;

    /** 目标产线停台原因（停台时挂接） */
    @Transient
    private String toLineHaltReason;

    /** 目标产线预计复台时间（停台时挂接） */
    @Transient
    private java.time.LocalDateTime toLineExpectedResumeTime;
}
