package com.spring.transfer.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "spring_archive")
public class SpringArchive {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spring_code", nullable = false, unique = true, length = 32)
    private String springCode;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "elastic_coefficient", nullable = false, precision = 10, scale = 4)
    private BigDecimal elasticCoefficient;

    @Column(name = "outer_diameter", nullable = false, precision = 10, scale = 4)
    private BigDecimal outerDiameter;

    @Column(name = "current_line_id", nullable = false)
    private Long currentLineId;

    @Transient
    private String currentLineName;

    @Column(name = "initial_line_id", nullable = false)
    private Long initialLineId;

    @Transient
    private String initialLineName;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}
