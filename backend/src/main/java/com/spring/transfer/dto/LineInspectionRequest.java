package com.spring.transfer.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 开班点检登记：质量员登记气源压力、工装完好情况与点检人，三项缺一不可提交
 */
@Data
public class LineInspectionRequest {
    @NotNull(message = "点检产线不能为空")
    private Long lineId;

    @NotNull(message = "气源压力不能为空")
    @DecimalMin(value = "0.0", message = "气源压力不能为负数")
    @DecimalMax(value = "2.0", message = "气源压力不能超过 2.0 MPa")
    private BigDecimal airPressure;

    @NotNull(message = "工装完好情况不能为空")
    private Boolean toolingIntact;

    @NotBlank(message = "点检人不能为空")
    @Size(max = 32, message = "点检人长度不能超过32个字符")
    private String inspector;
}
