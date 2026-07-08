package com.spring.transfer.controller;

import com.spring.transfer.common.ApiResponse;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.service.ProductionLineService;
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
}
