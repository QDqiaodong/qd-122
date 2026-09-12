package com.spring.transfer.controller;

import com.spring.transfer.common.ApiResponse;
import com.spring.transfer.dto.LineHaltGuardResponse;
import com.spring.transfer.dto.LineHaltRequest;
import com.spring.transfer.dto.LineResumeRequest;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.service.ProductionLineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/lines")
@RequiredArgsConstructor
public class ProductionLineController {
    private final ProductionLineService productionLineService;

    @GetMapping
    public ApiResponse<List<ProductionLine>> findAll() {
        return ApiResponse.success(productionLineService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductionLine> findById(@PathVariable Long id) {
        return productionLineService.findById(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(404, "产线不存在"));
    }

    @PostMapping
    public ApiResponse<ProductionLine> save(@RequestBody ProductionLine productionLine) {
        try {
            return ApiResponse.success(productionLineService.save(productionLine));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 登记停台前查询影响：返回停台状态与流向该产线的待审批申请量，供页面提示 */
    @GetMapping("/{id}/halt-guard")
    public ApiResponse<LineHaltGuardResponse> haltGuard(@PathVariable Long id) {
        try {
            return ApiResponse.success(productionLineService.getHaltGuard(id));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 调度员登记产线临时停台：填写停台原因与预计复台时间 */
    @PostMapping("/{id}/halt")
    public ApiResponse<ProductionLine> halt(@PathVariable Long id,
                                            @Valid @RequestBody LineHaltRequest request) {
        try {
            return ApiResponse.success(productionLineService.halt(id, request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 产线复台：必须填写复台结论 */
    @PostMapping("/{id}/resume")
    public ApiResponse<ProductionLine> resume(@PathVariable Long id,
                                              @Valid @RequestBody LineResumeRequest request) {
        try {
            return ApiResponse.success(productionLineService.resume(id, request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
