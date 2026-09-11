package com.spring.transfer.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * 调拨模拟预估请求：一组弹簧 + 拟接收产线。
 */
@Data
public class SimulateRequest {
    @NotEmpty(message = "弹簧ID列表不能为空")
    private List<Long> springIds;

    @NotNull(message = "拟接收产线ID不能为空")
    private Long toLineId;
}
