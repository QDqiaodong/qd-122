package com.spring.transfer.controller;

import com.spring.transfer.common.ApiResponse;
import com.spring.transfer.common.ApplicationStatus;
import com.spring.transfer.dto.ApplicationDetailResponse;
import com.spring.transfer.dto.ApprovalRequest;
import com.spring.transfer.dto.ItemProcessResult;
import com.spring.transfer.dto.SubmitApplicationRequest;
import com.spring.transfer.entity.TransferApplication;
import com.spring.transfer.service.TransferApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/transfer-applications")
@RequiredArgsConstructor
public class TransferApplicationController {
    private final TransferApplicationService applicationService;

    @GetMapping
    public ApiResponse<Page<TransferApplication>> findAll(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean halted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "applyTime"));
        return ApiResponse.success(applicationService.findAll(status, keyword, halted, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<ApplicationDetailResponse> getDetail(@PathVariable Long id) {
        return ApiResponse.success(applicationService.getDetail(id));
    }

    @PostMapping
    public ApiResponse<TransferApplication> submit(@Valid @RequestBody SubmitApplicationRequest request) {
        try {
            return ApiResponse.success(applicationService.submit(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/approve")
    public ApiResponse<List<ItemProcessResult>> approve(@Valid @RequestBody ApprovalRequest request) {
        try {
            return ApiResponse.success(applicationService.approve(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/reject")
    public ApiResponse<List<ItemProcessResult>> reject(@Valid @RequestBody ApprovalRequest request) {
        try {
            return ApiResponse.success(applicationService.reject(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
