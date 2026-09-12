package com.spring.transfer.controller;

import com.spring.transfer.common.ApiResponse;
import com.spring.transfer.common.SampleStatus;
import com.spring.transfer.dto.CloseSampleRequest;
import com.spring.transfer.dto.RegisterSampleRequest;
import com.spring.transfer.dto.UpdateMeasuredRequest;
import com.spring.transfer.entity.ElasticSample;
import com.spring.transfer.service.ElasticSampleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

/**
 * 弹力抽检留样：质量员按产线登记实测弹力系数、判定偏离、闭环处置。
 */
@RestController
@RequestMapping("/api/elastic-samples")
@RequiredArgsConstructor
public class ElasticSampleController {
    private final ElasticSampleService elasticSampleService;

    /**
     * 留样单分页查询，可按 待闭环(OPEN)/已闭环(CLOSED)、产线、是否偏离、关键词筛选。
     * 默认待闭环优先、登记时间倒序（status DESC, createTime DESC）。
     */
    @GetMapping
    public ApiResponse<Page<ElasticSample>> findAll(
            @RequestParam(required = false) SampleStatus status,
            @RequestParam(required = false) Long lineId,
            @RequestParam(required = false) Boolean deviated,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // OPEN > CLOSED 字典序降序，使待闭环单排在前面
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("status"), Sort.Order.desc("createTime"), Sort.Order.desc("id")));
        try {
            return ApiResponse.success(
                    elasticSampleService.findAll(status, lineId, deviated, keyword, pageable));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<ElasticSample> findById(@PathVariable Long id) {
        try {
            return ApiResponse.success(elasticSampleService.findById(id));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 登记留样：写留样编号、实测系数，系统按该线适用区间判定是否偏离 */
    @PostMapping
    public ApiResponse<ElasticSample> register(@Valid @RequestBody RegisterSampleRequest request) {
        try {
            return ApiResponse.success(elasticSampleService.register(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 修改实测系数：仅待闭环单可改，偏离标记按登记产线适用区间重算 */
    @PutMapping("/{id}/measured")
    public ApiResponse<ElasticSample> updateMeasured(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateMeasuredRequest request) {
        try {
            return ApiResponse.success(elasticSampleService.updateMeasured(id, request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 闭环：处置结论必填，不写不能闭环；闭环后实测系数锁定不可改 */
    @PostMapping("/{id}/close")
    public ApiResponse<ElasticSample> close(@PathVariable Long id,
                                            @Valid @RequestBody CloseSampleRequest request) {
        try {
            return ApiResponse.success(elasticSampleService.close(id, request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
