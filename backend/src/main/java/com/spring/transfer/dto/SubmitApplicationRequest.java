package com.spring.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class SubmitApplicationRequest {
    @NotEmpty(message = "弹簧ID列表不能为空")
    private List<Long> springIds;

    @NotNull(message = "目标产线ID不能为空")
    private Long toLineId;

    @NotBlank(message = "申请人不能为空")
    @Size(max = 32, message = "申请人长度不能超过32个字符")
    private String applicant;

    @NotBlank(message = "申请原因不能为空")
    @Size(max = 255, message = "申请原因长度不能超过255个字符")
    private String reason;
}
