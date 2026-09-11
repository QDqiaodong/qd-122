package com.spring.transfer.entity;

import com.spring.transfer.common.SimulationStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/**
 * 工序调拨模拟方案：调度员保存的一组弹簧划至目标产线的负载预估快照。
 * 采用时生成待审批划转申请，方案与申请单一一对应。
 */
@Data
@Entity
@Table(name = "transfer_simulation")
public class TransferSimulation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "simulation_no", nullable = false, unique = true, length = 32)
    private String simulationNo;

    @Column(name = "operator", nullable = false, length = 32)
    private String operator;

    @Column(name = "to_line_id", nullable = false)
    private Long toLineId;

    @Column(name = "to_line_name", nullable = false, length = 64)
    private String toLineName;

    @Column(name = "remark", length = 255)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SimulationStatus status = SimulationStatus.DRAFT;

    /** 保存方案时的负载预估快照（JSON），保证刷新后预估结果与保存时一致 */
    @Column(name = "estimate_snapshot", columnDefinition = "TEXT")
    private String estimateSnapshot;

    /** 采用后生成的划转申请单ID */
    @Column(name = "application_id")
    private Long applicationId;

    /** 采用后生成的划转申请单号 */
    @Column(name = "application_no", length = 32)
    private String applicationNo;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @Transient
    private Integer itemCount;

    /** 关联划转申请单的实时状态（PENDING/APPROVED/REJECTED/PARTIAL） */
    @Transient
    private String applicationStatus;
}
