package com.spring.transfer.controller;

import com.spring.transfer.common.ApiResponse;
import com.spring.transfer.dto.BatchTransferRequest;
import com.spring.transfer.entity.TransferRecord;
import com.spring.transfer.service.TransferRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferRecordController {
    private final TransferRecordService transferRecordService;

    @GetMapping
    public ApiResponse<Page<TransferRecord>> findAll(
            @RequestParam(required = false) Long springId,
            @RequestParam(required = false) Long lineId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "operateTime"));
        return ApiResponse.success(transferRecordService.findAll(springId, lineId, pageable));
    }

    @PostMapping
    public ApiResponse<List<TransferRecord>> batchTransfer(@Valid @RequestBody BatchTransferRequest request) {
        try {
            return ApiResponse.success(transferRecordService.batchTransfer(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
