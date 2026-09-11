package com.spring.transfer.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 调拨模拟方案明细：参与模拟的弹簧及其模拟时的归属产线快照。
 */
@Data
@Entity
@Table(name = "transfer_simulation_item")
public class TransferSimulationItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "simulation_id", nullable = false)
    private Long simulationId;

    @Column(name = "spring_id", nullable = false)
    private Long springId;

    @Column(name = "spring_code", nullable = false, length = 32)
    private String springCode;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "elastic_coefficient", nullable = false, precision = 10, scale = 4)
    private BigDecimal elasticCoefficient;

    @Column(name = "from_line_id", nullable = false)
    private Long fromLineId;

    @Column(name = "from_line_name", nullable = false, length = 64)
    private String fromLineName;
}
