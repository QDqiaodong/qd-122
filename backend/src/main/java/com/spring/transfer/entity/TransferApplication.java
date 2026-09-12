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
