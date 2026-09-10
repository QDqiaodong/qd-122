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
}
