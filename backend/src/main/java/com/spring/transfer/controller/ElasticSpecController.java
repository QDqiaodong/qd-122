package com.spring.transfer.controller;

import com.spring.transfer.common.ApiResponse;
import com.spring.transfer.service.ElasticSpecCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/specs")
@RequiredArgsConstructor
public class ElasticSpecController {
    private final ElasticSpecCacheService elasticSpecCacheService;

    @GetMapping("/elastic-force")
    public ApiResponse<List<Double>> getElasticSpecs(
            @RequestParam(required = false) Double min,
            @RequestParam(required = false) Double max) {
        return ApiResponse.success(elasticSpecCacheService.getElasticSpecs(min, max));
    }
}
