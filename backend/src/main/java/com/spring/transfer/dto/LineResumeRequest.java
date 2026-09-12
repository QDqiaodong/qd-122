package com.spring.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 产线复台：必须填写复台结论（停台原因核实/处置结果）
 */
@Data
public class LineResumeRequest {
    @NotBlank(message = "操作人不能为空")
    @Size(max = 32, message = "操作人长度不能超过32个字符")
    private String operator;

    @NotBlank(message = "复台必须填写结论")
    @Size(max = 255, message = "复台结论长度不能超过255个字符")
    private String conclusion;
}
