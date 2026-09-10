package com.spring.transfer.entity;

import com.spring.transfer.common.ItemStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "transfer_application_item")
public class TransferApplicationItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "spring_id", nullable = false)
    private Long springId;

    @Column(name = "spring_code", nullable = false, length = 32)
    private String springCode;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "elastic_coefficient", nullable = false, precision = 10, scale = 4)
    private BigDecimal elasticCoefficient;

    @Column(name = "outer_diameter", nullable = false, precision = 10, scale = 4)
    private BigDecimal outerDiameter;

    @Column(name = "from_line_id", nullable = false)
    private Long fromLineId;

    @Column(name = "from_line_name", nullable = false, length = 64)
    private String fromLineName;

    @Column(name = "to_line_id", nullable = false)
    private Long toLineId;

    @Column(name = "to_line_name", nullable = false, length = 64)
    private String toLineName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ItemStatus status = ItemStatus.PENDING;

    @Column(name = "approver", length = 32)
    private String approver;

    @Column(name = "approve_time")
    private LocalDateTime approveTime;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "transfer_record_id")
    private Long transferRecordId;
}
