package com.spring.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

/**
 * 弹簧封存登记：质量员对抽检不合格/待复测弹簧登记封存原因与预计解封日
 */
@Data
public class SealRequest {
    @NotBlank(message = "操作人不能为空")
    @Size(max = 32, message = "操作人长度不能超过32个字符")
    private String operator;

    @NotBlank(message = "封存原因不能为空")
    @Size(max = 255, message = "封存原因长度不能超过255个字符")
    private String reason;

    @NotNull(message = "预计解封日不能为空")
    private LocalDate expectedUnsealDate;
}
