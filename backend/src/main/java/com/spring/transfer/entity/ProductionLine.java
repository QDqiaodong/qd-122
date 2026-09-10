package com.spring.transfer.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "production_line")
public class ProductionLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_code", nullable = false, unique = true, length = 32)
    private String lineCode;

    @Column(name = "line_name", nullable = false, length = 64)
    private String lineName;

    @Column(name = "description", length = 255)
    private String description;

    /** 日承载阈值：该产线单日可承载的弹簧数量上限，null 表示尚未配置 */
    @Column(name = "daily_capacity_threshold")
    private Integer dailyCapacityThreshold;

    /** 适用弹力系数下限（N/mm），null 表示不限制 */
    @Column(name = "elastic_min", precision = 10, scale = 4)
    private BigDecimal elasticMin;

    /** 适用弹力系数上限（N/mm），null 表示不限制 */
    @Column(name = "elastic_max", precision = 10, scale = 4)
    private BigDecimal elasticMax;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}
