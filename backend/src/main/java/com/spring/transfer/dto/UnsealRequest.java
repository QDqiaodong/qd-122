package com.spring.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 弹簧解封：必须填写解封结论（复测结果/处置结论）
 */
@Data
public class UnsealRequest {
    @NotBlank(message = "操作人不能为空")
    @Size(max = 32, message = "操作人长度不能超过32个字符")
    private String operator;

    @NotBlank(message = "解封必须填写结论")
    @Size(max = 255, message = "解封结论长度不能超过255个字符")
    private String conclusion;
}
