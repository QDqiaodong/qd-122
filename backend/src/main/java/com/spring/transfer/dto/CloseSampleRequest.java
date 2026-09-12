package com.spring.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 留样单闭环请求：处置结论必填，不写不能闭环。
 */
@Data
public class CloseSampleRequest {
    @NotBlank(message = "质量员不能为空")
    @Size(max = 32, message = "质量员姓名长度不能超过32个字符")
    private String operator;

    @NotBlank(message = "不写处置结论不能闭环")
    @Size(max = 512, message = "处置结论长度不能超过512个字符")
    private String conclusion;
}
