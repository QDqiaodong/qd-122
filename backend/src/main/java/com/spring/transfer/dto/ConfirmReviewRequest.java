package com.spring.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 确认夜班承载复核单：接班调度员必须填写跟进说明 */
@Data
public class ConfirmReviewRequest {
    @NotBlank(message = "确认人不能为空")
    @Size(max = 32, message = "确认人长度不能超过32个字符")
    private String confirmer;

    @NotBlank(message = "不写跟进说明不能确认")
    @Size(max = 512, message = "跟进说明长度不能超过512个字符")
    private String followUpNote;
}
