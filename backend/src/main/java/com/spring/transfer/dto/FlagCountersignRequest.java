package com.spring.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 黄标摘标加签：质量主管填写工号与加签说明，二者缺一不可。
 */
@Data
public class FlagCountersignRequest {
    @NotBlank(message = "请填写加签人工号")
    @Size(max = 32, message = "工号长度不能超过32个字符")
    private String operatorId;

    @NotBlank(message = "请填写加签说明")
    @Size(max = 255, message = "加签说明长度不能超过255个字符")
    private String note;
}
