package com.spring.transfer.controller;

import com.spring.transfer.common.ApiResponse;
import com.spring.transfer.dto.MeterReadingRequest;
import com.spring.transfer.entity.MeterReading;
import com.spring.transfer.service.MeterReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 产线电表抄录：每条产线每个班次（白班/夜班）留下一条读数；
 * 与上一条读数跳变超过约定幅度自动标异常；夜班复核提交前该线当天须已有白班抄录。
 */
@RestController
@RequestMapping("/api/meter-readings")
@RequiredArgsConstructor
public class MeterReadingController {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final MeterReadingService meterReadingService;

    /** 提交电表抄录：产线、班次、读数、抄表人四项必填，缺一不能提交 */
    @PostMapping
    public ApiResponse<MeterReading> submit(@Valid @RequestBody MeterReadingRequest request) {
        try {
            return ApiResponse.success(meterReadingService.submit(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 全部产线的最近一条抄录，key 为产线ID（列表展示最近读数与是否异常） */
    @GetMapping("/latest")
    public ApiResponse<Map<Long, MeterReading>> latest() {
        return ApiResponse.success(meterReadingService.latestMap());
    }

    /** 当天各产线白班抄录，key 为产线ID（夜班复核签发前判定该线当天是否已抄白班） */
    @GetMapping("/day-shift/today")
    public ApiResponse<Map<Long, MeterReading>> dayShiftToday() {
        LocalDate today = LocalDate.now(ZONE);
        return ApiResponse.success(meterReadingService.dayShiftMap(today));
    }

    /** 指定产线当天白班抄录（无则 data 为 null），夜班复核弹窗用于提交前提示 */
    @GetMapping("/day-shift")
    public ApiResponse<MeterReading> dayShift(@RequestParam Long lineId) {
        return ApiResponse.success(
                meterReadingService.findDayShift(lineId, LocalDate.now(ZONE)).orElse(null));
    }

    /** 指定产线的抄录历史（抄表时间倒序） */
    @GetMapping("/lines/{lineId}")
    public ApiResponse<List<MeterReading>> findByLine(@PathVariable Long lineId) {
        return ApiResponse.success(meterReadingService.findByLine(lineId));
    }
}
