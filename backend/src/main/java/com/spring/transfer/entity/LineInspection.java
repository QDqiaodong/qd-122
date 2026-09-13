package com.spring.transfer.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 产线开班点检记录：质量员开班前登记气源压力、工装完好情况与点检人，
 * 三项缺一不可提交。系统按标准气源压力区间与工装完好情况判定点检是否通过。
 *
 * 当日未完成开班点检（或点检未通过）的产线不能作为调拨模拟的接收方；
 * 负载预警看板展示该线最近一次点检时间与是否通过。
 */
@Data
@Entity
@Table(name = "line_inspection")
public class LineInspection {
    /** 标准气源压力下限（MPa），低于该值判定点检不通过 */
    public static final BigDecimal AIR_PRESSURE_MIN = new BigDecimal("0.40");
    /** 标准气源压力上限（MPa），高于该值判定点检不通过 */
    public static final BigDecimal AIR_PRESSURE_MAX = new BigDecimal("0.80");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 点检编号，如 INSP20260913073000001 */
    @Column(name = "inspection_no", nullable = false, unique = true, length = 32)
    private String inspectionNo;

    /** 点检产线ID */
    @Column(name = "line_id", nullable = false)
    private Long lineId;

    /** 产线编码（快照） */
    @Column(name = "line_code", nullable = false, length = 32)
    private String lineCode;

    /** 产线名称（快照） */
    @Column(name = "line_name", nullable = false, length = 64)
    private String lineName;

    /** 气源压力（MPa） */
    @Column(name = "air_pressure", nullable = false, precision = 5, scale = 3)
    private BigDecimal airPressure;

    /** 工装是否完好：true-完好 false-不完好 */
    @Column(name = "tooling_intact", nullable = false)
    private Boolean toolingIntact;

    /** 点检人（质量员） */
    @Column(name = "inspector", nullable = false, length = 32)
    private String inspector;

    /** 点检结论：true-通过 false-不通过（工装不完好、气源压力超出标准区间或剩余刀次低于门槛即为不通过） */
    @Column(name = "passed", nullable = false)
    private Boolean passed;

    /** 点检时该产线工装剩余刀次快照（null 表示当时尚未录入） */
    @Column(name = "tooling_remaining_cuts")
    private Integer toolingRemainingCuts;

    /** 点检时该产线工装剩余刀次门槛快照（null 表示当时尚未配置） */
    @Column(name = "tooling_cut_threshold")
    private Integer toolingCutThreshold;

    /** 点检时剩余刀次是否已到门槛（低于门槛），作为不通过判定明细随记录持久化 */
    @Column(name = "tooling_below_threshold", nullable = false)
    private Boolean toolingBelowThreshold = false;

    /** 点检时间（登记时间） */
    @Column(name = "inspect_time", nullable = false)
    private LocalDateTime inspectTime;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 是否当日点检（按点检时间与服务端当前日期比较） */
    public boolean isToday() {
        return inspectTime != null && inspectTime.toLocalDate().equals(LocalDate.now());
    }

    /**
     * 点检结论摘要，用于被拦截时的明确原因提示，
     * 例如：气源压力 0.30 MPa（标准 0.40~0.80 MPa），工装完好，剩余刀次 30（门槛 100，已到门槛）
     */
    public String getResultSummary() {
        StringBuilder sb = new StringBuilder("气源压力 ")
                .append(airPressure == null ? "未登记" : airPressure.stripTrailingZeros().toPlainString() + " MPa");
        if (airPressure == null || airPressure.compareTo(AIR_PRESSURE_MIN) < 0
                || airPressure.compareTo(AIR_PRESSURE_MAX) > 0) {
            sb.append("（标准 ").append(AIR_PRESSURE_MIN.stripTrailingZeros().toPlainString())
                    .append("~").append(AIR_PRESSURE_MAX.stripTrailingZeros().toPlainString()).append(" MPa）");
        }
        sb.append("，工装").append(Boolean.TRUE.equals(toolingIntact) ? "完好" : "不完好");
        // 剩余刀次已到门槛时给出剩余/门槛明细，便于换刀员核对换刀
        if (Boolean.TRUE.equals(toolingBelowThreshold)) {
            sb.append("，剩余刀次 ").append(toolingRemainingCuts == null ? "未录入" : toolingRemainingCuts)
                    .append("（门槛 ").append(toolingCutThreshold == null ? "未配置" : toolingCutThreshold)
                    .append("，已到门槛）");
        }
        return sb.toString();
    }
}
