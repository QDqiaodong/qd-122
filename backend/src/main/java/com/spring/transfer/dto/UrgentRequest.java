package com.spring.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 加急/取消加急请求：标记加急必须填写加急原因，取消加急必须填写取消说明，
 * 均记录操作人（调度员）以便追溯
 */
@Data
public class UrgentRequest {
    @NotBlank(message = "操作人不能为空")
    @Size(max = 32, message = "操作人长度不能超过32个字符")
    private String operator;

    /** 标记加急时为加急原因，取消加急时为取消说明 */
    @NotBlank(message = "加急原因/取消说明不能为空")
    @Size(max = 255, message = "加急原因/取消说明长度不能超过255个字符")
    private String reason;
}
