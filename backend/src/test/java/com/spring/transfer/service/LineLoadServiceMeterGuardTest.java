package com.spring.transfer.service;

import com.spring.transfer.common.MeterShift;
import com.spring.transfer.dto.LineLoadStats;
import com.spring.transfer.dto.LineThresholdUpdateRequest;
import com.spring.transfer.entity.MeterReading;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.SpringArchiveRepository;
import com.spring.transfer.repository.TransferApplicationRepository;
import com.spring.transfer.repository.TransferRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 电表读数与负载看板/日承载门槛联动的单测：
 * 看板概览挂载最近一次电表读数与是否异常；最近一次抄录读数异常的产线不能修改日承载门槛，
 * 保存时给出明确拦截原因；读数正常或从未抄录的产线不受影响。
 */
@ExtendWith(MockitoExtension.class)
class LineLoadServiceMeterGuardTest {

    @Mock
    private ProductionLineRepository productionLineRepository;
    @Mock
    private SpringArchiveRepository springArchiveRepository;
    @Mock
    private TransferRecordRepository transferRecordRepository;
    @Mock
    private TransferApplicationRepository applicationRepository;
    @Mock
    private LoadAlertService loadAlertService;
    @Mock
    private LineInspectionService lineInspectionService;
    @Mock
    private MeterReadingService meterReadingService;

    private LineLoadService lineLoadService;

    @BeforeEach
    void setUp() {
        lineLoadService = new LineLoadService(productionLineRepository, springArchiveRepository,
                transferRecordRepository, applicationRepository, loadAlertService, lineInspectionService,
                meterReadingService);
    }

    @Test
    void abnormalLatestReadingBlocksThresholdUpdateWithReason() {
        ProductionLine line = line(1L);
        when(productionLineRepository.findById(1L)).thenReturn(Optional.of(line));
        when(meterReadingService.latestOfLine(1L))
                .thenReturn(Optional.of(reading(1L, "6500.00", true,
                        "读数较上一条突增 1300.00 kWh（上一条 5200.00，本条 6500.00），超过约定幅度 1000 kWh，请现场复核电表")));

        LineThresholdUpdateRequest request = new LineThresholdUpdateRequest();
        request.setDailyCapacityThreshold(8);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> lineLoadService.updateThreshold(1L, request));
        assertTrue(ex.getMessage().contains("装配一号线"));
        assertTrue(ex.getMessage().contains("电表抄录读数异常"));
        assertTrue(ex.getMessage().contains("突增 1300.00 kWh"));
        assertTrue(ex.getMessage().contains("不能修改日承载门槛"));
        // 拦截发生在任何写入之前
        verify(productionLineRepository, never()).save(any());
    }

    @Test
    void normalLatestReadingAllowsThresholdUpdate() {
        ProductionLine line = line(1L);
        when(productionLineRepository.findById(1L)).thenReturn(Optional.of(line));
        when(meterReadingService.latestOfLine(1L))
                .thenReturn(Optional.of(reading(1L, "5200.00", false, null)));
        stubUpdatePassThrough();
        when(productionLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LineThresholdUpdateRequest request = new LineThresholdUpdateRequest();
        request.setDailyCapacityThreshold(8);
        ProductionLine saved = lineLoadService.updateThreshold(1L, request);

        assertEquals(8, saved.getDailyCapacityThreshold());
        verify(productionLineRepository).save(any());
    }

    @Test
    void noReadingAllowsThresholdUpdate() {
        ProductionLine line = line(1L);
        when(productionLineRepository.findById(1L)).thenReturn(Optional.of(line));
        when(meterReadingService.latestOfLine(1L)).thenReturn(Optional.empty());
        stubUpdatePassThrough();
        when(productionLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LineThresholdUpdateRequest request = new LineThresholdUpdateRequest();
        request.setDailyCapacityThreshold(8);
        ProductionLine saved = lineLoadService.updateThreshold(1L, request);

        assertEquals(8, saved.getDailyCapacityThreshold());
        verify(productionLineRepository).save(any());
    }

    @Test
    void boardStatsCarryLatestMeterReading() {
        ProductionLine line = line(1L);
        when(productionLineRepository.findAll()).thenReturn(List.of(line));
        when(springArchiveRepository.findAll()).thenReturn(List.of());
        when(transferRecordRepository.findByOperateTimeAfter(any())).thenReturn(List.of());
        when(lineInspectionService.latestMap()).thenReturn(Map.of());
        MeterReading abnormal = reading(1L, "6500.00", true, "读数较上一条突增 1300.00 kWh，超过约定幅度 1000 kWh");
        when(meterReadingService.latestMap()).thenReturn(Map.of(1L, abnormal));
        when(loadAlertService.attachOpenEvents(any())).thenReturn(Map.of("pending", 0, "open", 0));
        when(applicationRepository.findUrgentPendingStats(any())).thenReturn(List.of());

        var board = lineLoadService.getBoard();

        assertEquals(1, board.getLines().size());
        LineLoadStats stats = board.getLines().get(0);
        assertEquals(0, new BigDecimal("6500.00").compareTo(stats.getLastMeterReadingValue()));
        assertEquals(Boolean.TRUE, stats.getLastMeterReadingAbnormal());
        assertEquals("DAY", stats.getLastMeterShift());
        assertEquals("张电工", stats.getLastMeterReader());
        assertNotNull(stats.getLastMeterReadTime());
        assertTrue(stats.getLastMeterAbnormalReason().contains("突增"));
    }

    @Test
    void boardStatsWithoutReadingLeaveMeterFieldsNull() {
        ProductionLine line = line(1L);
        when(productionLineRepository.findAll()).thenReturn(List.of(line));
        when(springArchiveRepository.findAll()).thenReturn(List.of());
        when(transferRecordRepository.findByOperateTimeAfter(any())).thenReturn(List.of());
        when(lineInspectionService.latestMap()).thenReturn(Map.of());
        when(meterReadingService.latestMap()).thenReturn(Map.of());
        when(loadAlertService.attachOpenEvents(any())).thenReturn(Map.of("pending", 0, "open", 0));
        when(applicationRepository.findUrgentPendingStats(any())).thenReturn(List.of());

        var board = lineLoadService.getBoard();

        LineLoadStats stats = board.getLines().get(0);
        assertNull(stats.getLastMeterReadingValue());
        assertNull(stats.getLastMeterReadingAbnormal());
        assertNull(stats.getLastMeterReader());
        assertNull(stats.getLastMeterReadTime());
    }

    /** 通过门禁后的公共链路：超载门禁、阈值保存与保存后事件同步 */
    private void stubUpdatePassThrough() {
        lenient().when(springArchiveRepository.findByCurrentLineId(1L)).thenReturn(List.of());
        lenient().when(loadAlertService.findOpenEvent(1L)).thenReturn(Optional.empty());
        // updateThreshold 保存后按新口径重算并同步事件
        lenient().when(springArchiveRepository.findAll()).thenReturn(List.of());
        lenient().when(transferRecordRepository.findByOperateTimeAfter(any())).thenReturn(List.of());
    }

    private ProductionLine line(Long id) {
        ProductionLine line = new ProductionLine();
        line.setId(id);
        line.setLineCode("LINE-00" + id);
        line.setLineName("装配一号线");
        line.setDailyCapacityThreshold(5);
        return line;
    }

    private MeterReading reading(Long lineId, String value, boolean abnormal, String abnormalReason) {
        MeterReading reading = new MeterReading();
        reading.setId(100L + lineId);
        reading.setLineId(lineId);
        reading.setShift(MeterShift.DAY);
        reading.setReadingValue(new BigDecimal(value));
        reading.setReader("张电工");
        reading.setReadTime(LocalDateTime.now());
        reading.setAbnormal(abnormal);
        reading.setAbnormalReason(abnormalReason);
        return reading;
    }
}
