package com.spring.transfer.controller;

import com.spring.transfer.common.AlertStatus;
import com.spring.transfer.common.ApiResponse;
import com.spring.transfer.dto.AlertDispositionRequest;
import com.spring.transfer.dto.LineLoadStats;
import com.spring.transfer.dto.LoadAlertEventResponse;
import com.spring.transfer.entity.LoadAlertEvent;
import com.spring.transfer.service.LineLoadService;
import com.spring.transfer.service.LoadAlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 产线负载告警事件处置闭环：
 * 事件由看板/明细负载计算自动生成，这里提供查询、确认处置与处理完成接口。
 */
@RestController
@RequestMapping("/api/line-load/alerts")
@RequiredArgsConstructor
public class LoadAlertController {
    private final LoadAlertService loadAlertService;
    private final LineLoadService lineLoadService;

    /** 全部告警事件（可按处置状态、产线筛选），看板「待处理事件」入口使用 */
    @GetMapping
    public ApiResponse<List<LoadAlertEventResponse>> list(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) Long lineId) {
        List<LoadAlertEvent> events = loadAlertService.findAllEvents(status, lineId);
        return ApiResponse.success(loadAlertService.toResponses(events, currentStatusMap(events)));
    }

    /** 告警事件详情：触发快照、处置信息与全部处置记录 */
    @GetMapping("/{eventId}")
    public ApiResponse<LoadAlertEventResponse> detail(@PathVariable Long eventId) {
        LoadAlertEvent event = loadAlertService.getEventOrThrow(eventId);
        String currentStatus = lineLoadService.getCurrentStatus(event.getLineId());
        return ApiResponse.success(loadAlertService.toResponse(event, currentStatus));
    }

    /** 确认责任人并填写处置计划（待处理 → 处置中；处置中可调整） */
    @PostMapping("/{eventId}/confirm")
    public ApiResponse<LoadAlertEventResponse> confirm(
            @PathVariable Long eventId,
            @Valid @RequestBody AlertDispositionRequest request) {
        LoadAlertEvent event = loadAlertService.confirm(eventId, request);
        String currentStatus = lineLoadService.getCurrentStatus(event.getLineId());
        return ApiResponse.success(loadAlertService.toResponse(event, currentStatus));
    }

    /** 标记处理完成并关闭事件（处置中 → 已关闭；需填写处理说明） */
    @PostMapping("/{eventId}/resolve")
    public ApiResponse<LoadAlertEventResponse> resolve(
            @PathVariable Long eventId,
            @Valid @RequestBody AlertDispositionRequest request) {
        LoadAlertEvent event = loadAlertService.getEventOrThrow(eventId);
        String currentStatus = lineLoadService.getCurrentStatus(event.getLineId());
        event = loadAlertService.resolve(eventId, request, currentStatus);
        return ApiResponse.success(loadAlertService.toResponse(event, currentStatus));
    }

    private Map<Long, String> currentStatusMap(List<LoadAlertEvent> events) {
        return events.stream()
                .map(LoadAlertEvent::getLineId)
                .distinct()
                .map(lineLoadService::getLineStats)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(
                        LineLoadStats::getLineId, LineLoadStats::getStatus, (a, b) -> a));
    }
}
