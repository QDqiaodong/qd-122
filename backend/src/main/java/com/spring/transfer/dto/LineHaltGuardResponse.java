package com.spring.transfer.dto;

import lombok.Data;

/**
 * 停台登记前的影响提示：返回产线当前停台状态及流向该产线的待审批申请数，
 * 供调度员登记停台时确认（已有待审批单需提示）。
 */
@Data
public class LineHaltGuardResponse {
    private Long lineId;
    private String lineCode;
    private String lineName;
    /** 当前是否停台中 */
    private boolean halted;
    /** 目标产线为该产线的待审批明细数（可能分布在多张申请单） */
    private long pendingItemCount;
    /** 涉及的待审批申请单数 */
    private long pendingApplicationCount;
}
