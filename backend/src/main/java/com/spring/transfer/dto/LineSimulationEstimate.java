package com.spring.transfer.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 单条受影响产线的模拟负载预估：划出/划入前后对比。
 */
@Data
public class LineSimulationEstimate {
    private Long lineId;
    private String lineCode;
    private String lineName;

    /** 划转方向：IN-拟接收产线（划入） OUT-弹簧划出产线 */
    private String direction;

    /** 本次模拟划入数量（仅接收产线 &gt; 0） */
    private Integer moveInCount;

    /** 本次模拟划出数量（仅划出产线 &gt; 0） */
    private Integer moveOutCount;

    /** 模拟前归属弹簧数量 */
    private Integer currentCount;

    /** 模拟后归属弹簧数量 */
    private Integer simulatedCount;

    private Integer dailyCapacityThreshold;

    /** 模拟前负载率（%），未配置阈值时为 null */
    private BigDecimal currentLoadRate;

    /** 模拟后负载率（%），未配置阈值时为 null */
    private BigDecimal simulatedLoadRate;

    /** 模拟前弹力系数越界数量 */
    private Integer currentOutOfRangeCount;

    /** 模拟后弹力系数越界数量 */
    private Integer simulatedOutOfRangeCount;

    /** 模拟前负载状态：NORMAL / WARNING / OVERLOAD */
    private String currentStatus;

    /** 模拟后负载状态：NORMAL / WARNING / OVERLOAD */
    private String simulatedStatus;

    /** 模拟后触发的预警/超载原因 */
    private List<String> reasons;
}
