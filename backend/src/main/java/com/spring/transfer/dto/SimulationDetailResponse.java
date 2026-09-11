package com.spring.transfer.dto;

import com.spring.transfer.entity.TransferSimulation;
import com.spring.transfer.entity.TransferSimulationItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

/**
 * 模拟方案详情：方案头 + 明细弹簧 + 保存时的负载预估快照。
 */
@Data
@AllArgsConstructor
public class SimulationDetailResponse {
    private TransferSimulation simulation;
    private List<TransferSimulationItem> items;
    private SimulationEstimateResponse estimate;
}
