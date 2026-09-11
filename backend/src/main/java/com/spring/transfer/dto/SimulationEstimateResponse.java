package com.spring.transfer.dto;

import lombok.Data;
import java.util.List;

/**
 * 调拨模拟预估结果：拟接收产线 + 各受影响产线的划出/划入负载预估。
 */
@Data
public class SimulationEstimateResponse {
    private Long toLineId;
    private String toLineName;

    /** 参与模拟的弹簧数量 */
    private Integer springCount;

    /** 受影响产线（接收产线 + 各划出产线）的预估明细 */
    private List<LineSimulationEstimate> lines;
}
