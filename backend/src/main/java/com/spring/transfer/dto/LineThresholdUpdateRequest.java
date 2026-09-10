package com.spring.transfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 产线日承载阈值与弹力系数适用区间维护请求。
 */
@Data
public class LineThresholdUpdateRequest {
    @Min(value = 1, message = "日承载阈值必须大于0")
    @Max(value = 100000, message = "日承载阈值超出合理范围")
    private Integer dailyCapacityThreshold;

    @DecimalMin(value = "0.0", message = "弹力系数下限不能为负数")
    private BigDecimal elasticMin;

    @DecimalMin(value = "0.0", message = "弹力系数上限不能为负数")
    private BigDecimal elasticMax;
}
