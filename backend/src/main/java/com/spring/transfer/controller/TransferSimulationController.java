package com.spring.transfer.controller;

import com.spring.transfer.common.ApiResponse;
import com.spring.transfer.common.SimulationStatus;
import com.spring.transfer.dto.AdoptSimulationRequest;
import com.spring.transfer.dto.SaveSimulationRequest;
import com.spring.transfer.dto.SimulateRequest;
import com.spring.transfer.dto.SimulationDetailResponse;
import com.spring.transfer.dto.SimulationEstimateResponse;
import com.spring.transfer.entity.TransferSimulation;
import com.spring.transfer.service.TransferSimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

/**
 * 工序调拨模拟：调度员选择弹簧与拟接收产线后预估各产线负载，
 * 可保存方案、采用（生成待审批划转申请）或作废。
 */
@RestController
@RequestMapping("/api/simulations")
@RequiredArgsConstructor
public class TransferSimulationController {
    private final TransferSimulationService simulationService;

    /** 实时负载预估（不保存方案） */
    @PostMapping("/preview")
    public ApiResponse<SimulationEstimateResponse> preview(@Valid @RequestBody SimulateRequest request) {
        try {
            return ApiResponse.success(simulationService.preview(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 保存模拟方案（含负载预估快照） */
    @PostMapping
    public ApiResponse<TransferSimulation> save(@Valid @RequestBody SaveSimulationRequest request) {
        try {
            return ApiResponse.success(simulationService.save(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping
    public ApiResponse<Page<TransferSimulation>> findAll(
            @RequestParam(required = false) SimulationStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return ApiResponse.success(simulationService.findAll(status, keyword, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<SimulationDetailResponse> getDetail(@PathVariable Long id) {
        try {
            return ApiResponse.success(simulationService.getDetail(id));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 采用方案：生成待审批划转申请，方案与申请状态持久关联 */
    @PostMapping("/{id}/adopt")
    public ApiResponse<TransferSimulation> adopt(
            @PathVariable Long id,
            @Valid @RequestBody AdoptSimulationRequest request) {
        try {
            return ApiResponse.success(simulationService.adopt(id, request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 作废方案 */
    @PostMapping("/{id}/discard")
    public ApiResponse<TransferSimulation> discard(@PathVariable Long id) {
        try {
            return ApiResponse.success(simulationService.discard(id));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
