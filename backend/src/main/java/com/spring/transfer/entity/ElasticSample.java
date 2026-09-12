package com.spring.transfer.entity;

import com.spring.transfer.common.SampleStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 弹力抽检留样单：质量员按产线登记抽检弹簧的实测弹力系数，
 * 系统比对该线适用区间（production_line.elastic_min/max）判断是否偏离。
 * 偏离件在摘标加签前于弹簧档案挂黄标（闭环不自动摘标，须质量主管在档案页加签），
 * 黄标件不能勾进划转申请。
 */
@Data
@Entity
@Table(name = "elastic_sample")
public class ElasticSample {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 留样编号，如 ES20260912093000001 */
    @Column(name = "sample_no", nullable = false, unique = true, length = 32)
    private String sampleNo;

    /** 抽检弹簧ID */
    @Column(name = "spring_id", nullable = false)
    private Long springId;

    /** 弹簧编号（快照） */
    @Column(name = "spring_code", nullable = false, length = 32)
    private String springCode;

    /** 型号（快照） */
    @Column(name = "model", nullable = false, length = 64)
    private String model;

    /** 登记时所在产线ID（按产线登记，快照） */
    @Column(name = "line_id", nullable = false)
    private Long lineId;

    /** 登记时所在产线编码（快照） */
    @Column(name = "line_code", nullable = false, length = 32)
    private String lineCode;

    /** 登记时所在产线名称（快照） */
    @Column(name = "line_name", nullable = false, length = 64)
    private String lineName;

    /** 实测弹力系数（待闭环可改，已闭环不可改） */
    @Column(name = "measured_coefficient", nullable = false, precision = 10, scale = 4)
    private BigDecimal measuredCoefficient;

    /** 登记时产线适用弹力系数下限快照 */
    @Column(name = "line_elastic_min", precision = 10, scale = 4)
    private BigDecimal lineElasticMin;

    /** 登记时产线适用弹力系数上限快照 */
    @Column(name = "line_elastic_max", precision = 10, scale = 4)
    private BigDecimal lineElasticMax;

    /** 是否偏离该线适用区间：0-在区间内 1-偏离 */
    @Column(name = "deviated", nullable = false)
    private Boolean deviated = false;

    /** 状态：OPEN-待闭环 CLOSED-已闭环 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @ColumnDefault("'OPEN'")
    private SampleStatus status = SampleStatus.OPEN;

    /** 登记质量员 */
    @Column(name = "operator", nullable = false, length = 32)
    private String operator;

    /** 处置结论：不写不能闭环 */
    @Column(name = "conclusion", length = 512)
    private String conclusion;

    /** 闭环操作人（质量员） */
    @Column(name = "close_operator", length = 32)
    private String closeOperator;

    /** 闭环时间 */
    @Column(name = "close_time")
    private LocalDateTime closeTime;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @Transient
    private String springCurrentLineName;

    /** 是否待闭环 */
    public boolean isOpen() {
        return status == SampleStatus.OPEN;
    }

    /** 是否偏离且未闭环（即黄标条件） */
    public boolean isOpenDeviation() {
        return isOpen() && Boolean.TRUE.equals(deviated);
    }

    /**
     * 偏离件黄标摘要，用于划转拦截等需要明确原因的场景，
     * 例如：SP-2024-0001（留样 ES202609120001：实测 0.9000，装配一号线适用区间 0.2000~1.0000）
     */
    public String getDeviationSummary() {
        return springCode + "（留样 " + sampleNo + "：实测 " + measuredCoefficient
                + "，" + lineName + "适用区间 " + rangeText() + "）";
    }

    /** 适用区间文本，区间端点为 null 表示该侧不限 */
    public String rangeText() {
        String low = lineElasticMin == null ? "不限" : lineElasticMin.stripTrailingZeros().toPlainString();
        String high = lineElasticMax == null ? "不限" : lineElasticMax.stripTrailingZeros().toPlainString();
        return low + " ~ " + high;
    }
}
