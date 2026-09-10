package com.spring.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class ApprovalRequest {
    @NotEmpty(message = "申请明细ID列表不能为空")
    private List<Long> itemIds;

    @NotBlank(message = "审批人不能为空")
    @Size(max = 32, message = "审批人长度不能超过32个字符")
    private String approver;

    /** 驳回时必填 */
    @Size(max = 255, message = "驳回原因长度不能超过255个字符")
    private String reason;
}
