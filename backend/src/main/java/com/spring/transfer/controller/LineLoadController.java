package com.spring.transfer.controller;

import com.spring.transfer.common.ApiResponse;
import com.spring.transfer.dto.LineLoadBoardResponse;
import com.spring.transfer.dto.LineLoadDetailResponse;
import com.spring.transfer.dto.LineThresholdUpdateRequest;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.service.LineLoadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/line-load")
@RequiredArgsConstructor
public class LineLoadController {
    private final LineLoadService lineLoadService;

    /** 负载预警看板：正常/预警/超载分组及顶部统计（同一份计算结果） */
    @GetMapping("/board")
    public ApiResponse<LineLoadBoardResponse> getBoard() {
        return ApiResponse.success(lineLoadService.getBoard());
    }

    /** 按产线查看弹簧明细、触发预警原因与近期划转趋势 */
    @GetMapping("/lines/{lineId}")
    public ApiResponse<LineLoadDetailResponse> getLineDetail(@PathVariable Long lineId) {
        return lineLoadService.getLineDetail(lineId)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(404, "产线不存在"));
    }

    /** 维护产线日承载阈值与弹力系数适用区间 */
    @PutMapping("/lines/{lineId}/threshold")
    public ApiResponse<ProductionLine> updateThreshold(
            @PathVariable Long lineId,
            @Valid @RequestBody LineThresholdUpdateRequest request) {
        return ApiResponse.success(lineLoadService.updateThreshold(lineId, request));
    }
}
