package com.spring.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 签发夜班承载复核单：按产线签发，交班调度员必填，备注可选 */
@Data
public class CreateReviewRequest {
    @NotNull(message = "复核产线不能为空")
    private Long lineId;

    @NotBlank(message = "签发人不能为空")
    @Size(max = 32, message = "签发人长度不能超过32个字符")
    private String operator;

    @Size(max = 512, message = "交班备注长度不能超过512个字符")
    private String handoverRemark;
}
