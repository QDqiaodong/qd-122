package com.spring.transfer.entity;

import com.spring.transfer.common.LineHaltStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Entity
@Table(name = "production_line")
public class ProductionLine {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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

    /** 当前工装剩余刀次，null 表示尚未录入 */
    @Column(name = "tooling_remaining_cuts")
    private Integer toolingRemainingCuts;

    /** 工装剩余刀次门槛，null 表示尚未配置；剩余刀次低于该值即到门槛 */
    @Column(name = "tooling_cut_threshold")
    private Integer toolingCutThreshold;

    /** 最近一次工装刀次登记/换刀复位的操作人（换刀员） */
    @Column(name = "tooling_operator", length = 32)
    private String toolingOperator;

    /** 最近一次工装刀次登记/换刀复位时间 */
    @Column(name = "tooling_update_time")
    private LocalDateTime toolingUpdateTime;

    /** 停台状态：NORMAL-正常 HALTED-停台中；存量行由列默认值 'NORMAL' 兜底 */
    @Enumerated(EnumType.STRING)
    @Column(name = "halt_status", nullable = false, length = 16)
    @ColumnDefault("'NORMAL'")
    private LineHaltStatus haltStatus = LineHaltStatus.NORMAL;

    /** 停台原因（设备检修/缺料/工艺调整等） */
    @Column(name = "halt_reason", length = 255)
    private String haltReason;

    /** 预计复台时间 */
    @Column(name = "halt_expected_resume_time")
    private LocalDateTime haltExpectedResumeTime;

    /** 停台操作人（调度员） */
    @Column(name = "halt_operator", length = 32)
    private String haltOperator;

    /** 停台时间 */
    @Column(name = "halt_time")
    private LocalDateTime haltTime;

    /** 复台操作人 */
    @Column(name = "resume_operator", length = 32)
    private String resumeOperator;

    /** 复台时间 */
    @Column(name = "resume_time")
    private LocalDateTime resumeTime;

    /** 复台结论（停台原因核实/处置结果） */
    @Column(name = "resume_conclusion", length = 255)
    private String resumeConclusion;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /** 是否处于停台中（历史数据可能为 null，按正常处理） */
    public boolean isHalted() {
        return haltStatus == LineHaltStatus.HALTED;
    }

    /**
     * 工装剩余刀次是否已到门槛：当前剩余刀次低于门槛即视为到门槛。
     * 剩余刀次或门槛尚未录入（null）时不参与判定，按未到门槛处理（由初始化器补默认值）。
     */
    public boolean isToolingBelowThreshold() {
        return toolingRemainingCuts != null && toolingCutThreshold != null
                && toolingRemainingCuts < toolingCutThreshold;
    }

    /**
     * 工装刀次状态摘要，用于开班点检不通过与调拨模拟拦截的明确原因提示，
     * 例如：剩余刀次 30 已低于门槛 100
     */
    public String getToolingCutsSummary() {
        String remaining = toolingRemainingCuts == null ? "未录入" : String.valueOf(toolingRemainingCuts);
        String threshold = toolingCutThreshold == null ? "未配置" : String.valueOf(toolingCutThreshold);
        return "工装剩余刀次 " + remaining + " 已低于门槛 " + threshold
                + "（需换刀复位后才能开班点检通过、作为划转接收方）";
    }

    /**
     * 停台信息摘要，用于被拦截划转申请的明确原因提示，
     * 例如：产线「装配四号线」临时停台（停台原因：设备检修，预计复台时间：2026-09-12 08:00）
     */
    public String getHaltSummary() {
        StringBuilder sb = new StringBuilder("产线「").append(lineName).append("」临时停台（停台原因：")
                .append(haltReason == null ? "未登记" : haltReason);
        if (haltExpectedResumeTime != null) {
            sb.append("，预计复台时间：").append(haltExpectedResumeTime.format(TIME_FORMATTER));
        }
        return sb.append("）").toString();
    }
}
