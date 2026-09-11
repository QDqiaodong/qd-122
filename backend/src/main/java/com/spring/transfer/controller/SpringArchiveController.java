package com.spring.transfer.controller;

import com.spring.transfer.common.ApiResponse;
import com.spring.transfer.common.SealStatus;
import com.spring.transfer.dto.SealRequest;
import com.spring.transfer.dto.UnsealRequest;
import com.spring.transfer.entity.SpringArchive;
import com.spring.transfer.entity.TransferRecord;
import com.spring.transfer.service.SpringArchiveService;
import com.spring.transfer.service.TransferRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/springs")
@RequiredArgsConstructor
public class SpringArchiveController {
    private final SpringArchiveService springArchiveService;
    private final TransferRecordService transferRecordService;

    @GetMapping
    public ApiResponse<Page<SpringArchive>> findAll(
            @RequestParam(required = false) Long lineId,
            @RequestParam(required = false) SealStatus sealStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return ApiResponse.success(springArchiveService.findAll(lineId, sealStatus, keyword, pageable));
    }

    @GetMapping("/group-by-line")
    public ApiResponse<Map<Long, List<SpringArchive>>> groupByLine() {
        return ApiResponse.success(springArchiveService.groupByLine());
    }

    @GetMapping("/{id}")
    public ApiResponse<SpringArchive> findById(@PathVariable Long id) {
        return springArchiveService.findById(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(404, "弹簧档案不存在"));
    }

    @GetMapping("/{id}/trace")
    public ApiResponse<List<TransferRecord>> getTrace(@PathVariable Long id) {
        return ApiResponse.success(transferRecordService.findBySpringId(id));
    }

    @PostMapping
    public ApiResponse<SpringArchive> save(@RequestBody SpringArchive springArchive) {
        try {
            return ApiResponse.success(springArchiveService.save(springArchive));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/seal")
    public ApiResponse<SpringArchive> seal(@PathVariable Long id, @Valid @RequestBody SealRequest request) {
        try {
            return ApiResponse.success(springArchiveService.seal(id, request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/unseal")
    public ApiResponse<SpringArchive> unseal(@PathVariable Long id, @Valid @RequestBody UnsealRequest request) {
        try {
            return ApiResponse.success(springArchiveService.unseal(id, request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
