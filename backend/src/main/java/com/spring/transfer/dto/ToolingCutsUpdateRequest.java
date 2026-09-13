package com.spring.transfer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工装剩余刀次维护请求：换刀员按产线登记/扣减当前工装剩余刀次，或换刀复位。
 * 剩余刀次与门槛必填，操作人必填；剩余刀次低于门槛即到门槛（开班点检不通过、不能作为调拨模拟接收方），
 * 换刀复位使剩余刀次回到门槛及以上即解除门槛。
 */
@Data
public class ToolingCutsUpdateRequest {
    @NotNull(message = "工装剩余刀次不能为空")
    @Min(value = 0, message = "工装剩余刀次不能为负数")
    @Max(value = 1_000_000, message = "工装剩余刀次超出合理范围")
    private Integer toolingRemainingCuts;

    @NotNull(message = "刀次门槛不能为空")
    @Min(value = 1, message = "刀次门槛必须大于0")
    @Max(value = 1_000_000, message = "刀次门槛超出合理范围")
    private Integer toolingCutThreshold;

    @NotBlank(message = "操作人不能为空")
    @Size(max = 32, message = "操作人长度不能超过32个字符")
    private String operator;

    /** 是否为换刀复位（true 时本次登记按换刀处理，用于看板标识），默认 false */
    private Boolean replaceTool = false;
}
