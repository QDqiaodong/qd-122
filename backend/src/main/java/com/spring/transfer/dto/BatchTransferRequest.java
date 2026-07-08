package com.spring.transfer.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class BatchTransferRequest {
    @NotEmpty(message = "弹簧ID列表不能为空")
    private List<Long> springIds;

    @NotNull(message = "目标产线ID不能为空")
    private Long toLineId;

    @NotNull(message = "操作人不能为空")
    private String operator;

    private String remark;
}
