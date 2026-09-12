package com.spring.transfer.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 产线负载概览项，用于首页看板分组展示。
 */
@Data
public class LineLoadStats {
    private Long lineId;
    private String lineCode;
    private String lineName;
    private String description;

    /** 负载状态：NORMAL / WARNING / OVERLOAD */
    private String status;

    /** 日承载阈值，null 表示未配置 */
    private Integer dailyCapacityThreshold;

    /** 当前归属弹簧数量 */
    private Integer springCount;

    /** 数量负载率（当前数量 / 阈值 * 100），未配置阈值时为 null */
    private BigDecimal loadRate;

    /** 弹力系数适用区间下限 */
    private BigDecimal elasticMin;

    /** 弹力系数适用区间上限 */
    private BigDecimal elasticMax;

    /** 当前归属弹簧中超出适用弹力系数区间的数量 */
    private Integer outOfRangeCount;

    /** 最近趋势天数（固定 7 天） */
    private Integer trendDays;

    /** 近 trendDays 天划入数量 */
    private Integer recentInCount;

    /** 近 trendDays 天划出数量 */
    private Integer recentOutCount;

    /** 近 trendDays 天净流入（划入 - 划出） */
    private Integer recentNetIn;

    /** 预警触发原因，超载/预警时非空，正常时为空集合 */
    private java.util.List<String> reasons;

    /** 当前未关闭的告警事件ID（待处理/处置中），无未关闭事件时为 null */
    private Long openAlertEventId;

    /** 未关闭告警事件的处置状态：PENDING-待处理 / PROCESSING-处置中，无事件时为 null */
    private String openAlertStatus;

    /** 产线当前是否临时停台（停台期间不能作为划转接收方） */
    private boolean halted;

    /** 停台原因，未停台时为 null */
    private String haltReason;

    /** 预计复台时间，未停台时为 null */
    private java.time.LocalDateTime haltExpectedResumeTime;

    /** 停台操作人 */
    private String haltOperator;

    /** 停台登记时间 */
    private java.time.LocalDateTime haltTime;

    /** 最近一次复台操作人 */
    private String resumeOperator;

    /** 最近一次复台时间 */
    private java.time.LocalDateTime resumeTime;

    /** 最近一次复台结论 */
    private String resumeConclusion;
}
