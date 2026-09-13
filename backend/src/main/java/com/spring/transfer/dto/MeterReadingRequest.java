package com.spring.transfer.dto;

import com.spring.transfer.common.MeterShift;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 电表抄录提交：产线、班次、读数、抄表人四项必填，缺一不能提交。
 * 每条产线每个班次当天只能提交一条；是否异常由系统按与上一条的读数跳变判定。
 */
@Data
public class MeterReadingRequest {
    @NotNull(message = "抄表产线不能为空")
    private Long lineId;

    @NotNull(message = "班次不能为空（白班/夜班）")
    private MeterShift shift;

    @NotNull(message = "电表读数不能为空")
    @DecimalMin(value = "0.0", message = "电表读数不能为负数")
    private BigDecimal readingValue;

    @NotBlank(message = "抄表人不能为空")
    @Size(max = 32, message = "抄表人长度不能超过32个字符")
    private String reader;
}
