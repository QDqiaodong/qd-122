package com.spring.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 告警处置请求：
 * action=CONFIRM 确认责任人与处置计划（首次确认或补充/调整）；
 * action=RESOLVE 标记处理完成并关闭事件（remark 为处理说明）。
 */
@Data
public class AlertDispositionRequest {

    /** 处置动作：CONFIRM-确认处置 / RESOLVE-处理完成 */
    @NotBlank(message = "处置动作不能为空")
    private String action;

    /** 调度员（处置操作人） */
    @NotBlank(message = "调度员不能为空")
    private String operator;

    /** 责任人，CONFIRM 时必填 */
    @Size(max = 32, message = "责任人不能超过32个字符")
    private String responsiblePerson;

    /** 处置计划，CONFIRM 时必填 */
    @Size(max = 512, message = "处置计划不能超过512个字符")
    private String handlePlan;

    /** 备注；CONFIRM 时可选（追加备注），RESOLVE 时必填（处理说明） */
    @Size(max = 512, message = "备注不能超过512个字符")
    private String remark;
}
