package com.spring.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

/**
 * 保存调拨模拟方案请求。
 */
@Data
public class SaveSimulationRequest {
    @NotEmpty(message = "弹簧ID列表不能为空")
    private List<Long> springIds;

    @NotNull(message = "拟接收产线ID不能为空")
    private Long toLineId;

    @NotBlank(message = "调度员不能为空")
    @Size(max = 32, message = "调度员长度不能超过32个字符")
    private String operator;

    @Size(max = 255, message = "备注长度不能超过255个字符")
    private String remark;
}
