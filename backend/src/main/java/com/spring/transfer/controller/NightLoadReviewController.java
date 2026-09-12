package com.spring.transfer.controller;

import com.spring.transfer.common.ApiResponse;
import com.spring.transfer.common.ReviewStatus;
import com.spring.transfer.dto.ConfirmReviewRequest;
import com.spring.transfer.dto.CreateReviewRequest;
import com.spring.transfer.dto.NightLoadReviewResponse;
import com.spring.transfer.dto.ReviewGuardResponse;
import com.spring.transfer.service.NightLoadReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

/**
 * 夜班承载复核单。
 * 交班调度员按产线签发承载快照；接班调度员确认（必填跟进说明）后才能提交新划转。
 */
@RestController
@RequestMapping("/api/night-reviews")
@RequiredArgsConstructor
public class NightLoadReviewController {
    private final NightLoadReviewService reviewService;

    /** 复核单列表，支持按 PENDING-待确认 / CONFIRMED-已确认 筛选（不传为全部） */
    @GetMapping
    public ApiResponse<Page<NightLoadReviewResponse>> findAll(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issueTime"));
        return ApiResponse.success(reviewService.findAll(status, pageable));
    }

    /** 接班看板门禁：是否还有未确认复核单（决定能否提交新划转） */
    @GetMapping("/guard")
    public ApiResponse<ReviewGuardResponse> getGuard() {
        return ApiResponse.success(reviewService.getGuard());
    }

    @GetMapping("/{id}")
    public ApiResponse<NightLoadReviewResponse> getDetail(@PathVariable Long id) {
        return ApiResponse.success(reviewService.getDetail(id));
    }

    /** 交班调度员按产线签发复核单（快照数字由系统按看板口径计算） */
    @PostMapping
    public ApiResponse<NightLoadReviewResponse> issue(@Valid @RequestBody CreateReviewRequest request) {
        try {
            return ApiResponse.success(reviewService.issue(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 接班调度员确认复核单：不写跟进说明不能确认；已确认单不能再改 */
    @PostMapping("/{id}/confirm")
    public ApiResponse<NightLoadReviewResponse> confirm(@PathVariable Long id,
                                                        @Valid @RequestBody ConfirmReviewRequest request) {
        try {
            return ApiResponse.success(reviewService.confirm(id, request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
