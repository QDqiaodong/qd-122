package com.spring.transfer.controller;

import com.spring.transfer.common.ApiResponse;
import com.spring.transfer.common.ApplicationStatus;
import com.spring.transfer.dto.ApplicationDetailResponse;
import com.spring.transfer.dto.ApprovalRequest;
import com.spring.transfer.dto.ItemProcessResult;
import com.spring.transfer.dto.SubmitApplicationRequest;
import com.spring.transfer.dto.UrgentRequest;
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
            @RequestParam(required = false) Boolean urgent,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 审批台默认加急单优先，其余按申请时间倒序
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "urgent").and(Sort.by(Sort.Direction.DESC, "applyTime")));
        return ApiResponse.success(applicationService.findAll(status, keyword, halted, urgent, pageable));
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

    /** 调度员标记加急：必须填写加急原因；已结案单据不能再加急，重复加急给出明确原因 */
    @PostMapping("/{id}/urgent")
    public ApiResponse<TransferApplication> markUrgent(@PathVariable Long id,
                                                       @Valid @RequestBody UrgentRequest request) {
        try {
            return ApiResponse.success(applicationService.markUrgent(id, request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 取消加急：必须填写取消说明（记入操作记录） */
    @PostMapping("/{id}/cancel-urgent")
    public ApiResponse<TransferApplication> cancelUrgent(@PathVariable Long id,
                                                         @Valid @RequestBody UrgentRequest request) {
        try {
            return ApiResponse.success(applicationService.cancelUrgent(id, request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
