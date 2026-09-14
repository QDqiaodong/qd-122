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

    /** 最近一次电表读数（kWh），从未抄录为 null */
    private BigDecimal lastMeterReadingValue;

    /** 最近一次电表读数是否异常（跳变超约定幅度），从未抄录为 null；异常时不能修改日承载门槛 */
    private Boolean lastMeterReadingAbnormal;

    /** 最近一次抄表班次：DAY-白班 NIGHT-夜班，从未抄录为 null */
    private String lastMeterShift;

    /** 最近一次抄表人，从未抄录为 null */
    private String lastMeterReader;

    /** 最近一次抄表时间，从未抄录为 null */
    private java.time.LocalDateTime lastMeterReadTime;

    /** 最近一次读数异常原因（上一条/本条读数、跳变量），正常或未抄录为 null */
    private String lastMeterAbnormalReason;

    /** 最近一次开班点检时间，从未点检过为 null */
    private java.time.LocalDateTime lastInspectionTime;

    /** 最近一次开班点检是否通过，从未点检过为 null */
    private Boolean lastInspectionPassed;

    /** 最近一次开班点检的点检人（质量员） */
    private String lastInspector;

    /** 当日是否已完成开班点检（当日未点检的产线不能作为调拨模拟接收方） */
    private boolean inspectedToday;

    /** 最近一次超载处置的处置人（超载事件完成时必填），从未完成过超载处置为 null */
    private String lastOverloadDisposePerson;

    /** 最近一次超载处置的复核工号 */
    private String lastOverloadReviewEmployeeNo;

    /** 最近一次超载处置完成时间 */
    private java.time.LocalDateTime lastOverloadDisposeTime;

    /** 当前工装剩余刀次，尚未录入为 null */
    private Integer toolingRemainingCuts;

    /** 工装剩余刀次门槛，尚未配置为 null；剩余刀次低于门槛即到门槛 */
    private Integer toolingCutThreshold;

    /** 工装剩余刀次是否已到门槛（剩余刀次低于门槛）：到门槛时开班点检不通过、不能作为调拨模拟接收方 */
    private boolean toolingBelowThreshold;

    /** 最近一次工装刀次登记/换刀复位操作人 */
    private String toolingOperator;

    /** 最近一次工装刀次登记/换刀复位时间 */
    private java.time.LocalDateTime toolingUpdateTime;
}
