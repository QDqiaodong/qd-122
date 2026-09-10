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
}
