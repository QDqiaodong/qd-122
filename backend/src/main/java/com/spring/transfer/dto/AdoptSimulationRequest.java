package com.spring.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 采用模拟方案请求：采用时以申请人口径生成待审批划转申请。
 */
@Data
public class AdoptSimulationRequest {
    @NotBlank(message = "申请人不能为空")
    @Size(max = 32, message = "申请人长度不能超过32个字符")
    private String applicant;

    /** 申请原因，为空时使用方案默认原因 */
    @Size(max = 255, message = "申请原因长度不能超过255个字符")
    private String reason;
}
