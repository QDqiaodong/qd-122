package com.spring.transfer.controller;

import com.spring.transfer.common.ApiResponse;
import com.spring.transfer.dto.LineInspectionRequest;
import com.spring.transfer.entity.LineInspection;
import com.spring.transfer.service.LineInspectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * 产线开班点检：质量员开班前登记气源压力、工装完好与点检人；
 * 负载看板与调拨模拟按最近一次点检结果判定。
 */
@RestController
@RequestMapping("/api/line-inspections")
@RequiredArgsConstructor
public class LineInspectionController {
    private final LineInspectionService inspectionService;

    /** 登记开班点检：气源压力、工装完好、点检人三项必填，缺一不能提交 */
    @PostMapping
    public ApiResponse<LineInspection> register(@Valid @RequestBody LineInspectionRequest request) {
        try {
            return ApiResponse.success(inspectionService.register(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 全部产线的最近一次点检，key 为产线ID（档案页/模拟页判定「未开班点检」用） */
    @GetMapping("/latest")
    public ApiResponse<Map<Long, LineInspection>> latest() {
        return ApiResponse.success(inspectionService.latestMap());
    }

    /** 指定产线的点检历史（点检时间倒序） */
    @GetMapping("/lines/{lineId}")
    public ApiResponse<List<LineInspection>> findByLine(@PathVariable Long lineId) {
        return ApiResponse.success(inspectionService.findByLine(lineId));
    }
}
