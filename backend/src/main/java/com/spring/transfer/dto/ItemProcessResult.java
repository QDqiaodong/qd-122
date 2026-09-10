package com.spring.transfer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ItemProcessResult {
    private Long itemId;
    private String springCode;
    private boolean success;
    private String message;

    public static ItemProcessResult success(Long itemId, String springCode, String message) {
        return new ItemProcessResult(itemId, springCode, true, message);
    }

    public static ItemProcessResult fail(Long itemId, String springCode, String message) {
        return new ItemProcessResult(itemId, springCode, false, message);
    }
}
