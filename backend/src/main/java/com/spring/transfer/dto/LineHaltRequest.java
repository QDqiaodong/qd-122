package com.spring.transfer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 产线临时停台登记：调度员登记停台原因与预计复台时间
 */
@Data
public class LineHaltRequest {
    @NotBlank(message = "操作人不能为空")
    @Size(max = 32, message = "操作人长度不能超过32个字符")
    private String operator;

    @NotBlank(message = "停台原因不能为空")
    @Size(max = 255, message = "停台原因长度不能超过255个字符")
    private String reason;

    @NotNull(message = "预计复台时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expectedResumeTime;
}
