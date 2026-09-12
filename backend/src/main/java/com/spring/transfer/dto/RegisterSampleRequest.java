package com.spring.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 弹力抽检留样登记请求：质量员按产线登记。
 * lineId 可空（缺省取弹簧当前归属产线）；传入时必须与弹簧当前归属一致。
 */
@Data
public class RegisterSampleRequest {
    @NotNull(message = "弹簧不能为空")
    private Long springId;

    /** 登记产线ID，可空，缺省为弹簧当前归属产线 */
    private Long lineId;

    @NotNull(message = "实测弹力系数不能为空")
    @Positive(message = "实测弹力系数必须大于0")
    private BigDecimal measuredCoefficient;

    @NotBlank(message = "质量员不能为空")
    @Size(max = 32, message = "质量员姓名长度不能超过32个字符")
    private String operator;
}
