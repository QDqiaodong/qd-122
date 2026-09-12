package com.spring.transfer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 修改实测系数：仅待闭环留样单可改，偏离标记按登记时的产线适用区间快照重算。
 */
@Data
public class UpdateMeasuredRequest {
    @NotNull(message = "实测弹力系数不能为空")
    @Positive(message = "实测弹力系数必须大于0")
    private BigDecimal measuredCoefficient;
}
