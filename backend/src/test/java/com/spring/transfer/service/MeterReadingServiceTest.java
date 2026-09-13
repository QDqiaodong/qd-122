package com.spring.transfer.service;

import com.spring.transfer.common.MeterShift;
import com.spring.transfer.dto.MeterReadingRequest;
import com.spring.transfer.entity.MeterReading;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.repository.MeterReadingRepository;
import com.spring.transfer.repository.ProductionLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 电表抄录单测：
 * 每条产线每个班次当天只能留一条；与上一条读数跳变绝对值超过约定幅度（1000 kWh）标异常；
 * 夜班复核提交守卫要求该产线当天已有白班抄录。
 */
@ExtendWith(MockitoExtension.class)
class MeterReadingServiceTest {

    @Mock
    private MeterReadingRepository meterReadingRepository;
    @Mock
    private ProductionLineRepository productionLineRepository;

    private MeterReadingService meterReadingService;

    @BeforeEach
    void setUp() {
        meterReadingService = new MeterReadingService(meterReadingRepository, productionLineRepository);
    }

    @Test
    void firstReadingIsNotAbnormal() {
        when(productionLineRepository.findById(1L)).thenReturn(Optional.of(line(1L)));
        when(meterReadingRepository.findTopByLineIdOrderByReadTimeDescIdDesc(1L)).thenReturn(Optional.empty());
        when(meterReadingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MeterReading saved = meterReadingService.submit(request(1L, MeterShift.DAY, "5200"));

        assertFalse(saved.getAbnormal());
        assertNull(saved.getJumpDelta());
        assertNull(saved.getPreviousValue());
        assertEquals(MeterReading.JUMP_THRESHOLD_KWH, saved.getJumpThreshold());
        assertEquals("张电工", saved.getReader());
        assertEquals(MeterShift.DAY, saved.getShift());
        assertNotNull(saved.getReadTime());
    }

    @Test
    void jumpWithinThresholdIsNormal() {
        when(productionLineRepository.findById(1L)).thenReturn(Optional.of(line(1L)));
        when(meterReadingRepository.findTopByLineIdOrderByReadTimeDescIdDesc(1L))
                .thenReturn(Optional.of(previous("5200.00")));
        when(meterReadingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 恰好增加 1000：等于阈值不算「超过」，仍正常
        MeterReading edge = meterReadingService.submit(request(1L, MeterShift.NIGHT, "6200"));
        assertFalse(edge.getAbnormal());
        assertEquals(0, new BigDecimal("1000").compareTo(edge.getJumpDelta()));

        when(meterReadingRepository.findTopByLineIdOrderByReadTimeDescIdDesc(1L))
                .thenReturn(Optional.of(previous("5200.00")));
        MeterReading small = meterReadingService.submit(request(1L, MeterShift.NIGHT, "5450"));
        assertFalse(small.getAbnormal());
    }

    @Test
    void jumpOverThresholdIsMarkedAbnormal() {
        when(productionLineRepository.findById(1L)).thenReturn(Optional.of(line(1L)));
        when(meterReadingRepository.findTopByLineIdOrderByReadTimeDescIdDesc(1L))
                .thenReturn(Optional.of(previous("5200.00")));
        when(meterReadingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 突增 1300，超过约定幅度 1000 → 异常（仍如实保存读数）
        MeterReading surge = meterReadingService.submit(request(1L, MeterShift.NIGHT, "6500"));
        assertTrue(surge.getAbnormal());
        assertNotNull(surge.getAbnormalReason());
        assertTrue(surge.getAbnormalReason().contains("突增"));
        assertTrue(surge.getAbnormalReason().contains("1300"));
        assertEquals(new BigDecimal("6500"), surge.getReadingValue());

        // 读数回退 1200（绝对值超幅度）→ 同样异常，原因为回退
        when(meterReadingRepository.findTopByLineIdOrderByReadTimeDescIdDesc(1L))
                .thenReturn(Optional.of(previous("5200.00")));
        MeterReading rollback = meterReadingService.submit(request(1L, MeterShift.NIGHT, "4000"));
        assertTrue(rollback.getAbnormal());
        assertTrue(rollback.getAbnormalReason().contains("回退"));
        assertEquals(-1, rollback.getJumpDelta().signum());
    }

    @Test
    void duplicateShiftSameDayIsRejected() {
        ProductionLine l = line(2L);
        when(productionLineRepository.findById(2L)).thenReturn(Optional.of(l));
        when(meterReadingRepository.existsByLineIdAndReadingDateAndShift(
                org.mockito.ArgumentMatchers.eq(2L), any(LocalDate.class), org.mockito.ArgumentMatchers.eq(MeterShift.DAY)))
                .thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> meterReadingService.submit(request(2L, MeterShift.DAY, "1000")));
        assertTrue(ex.getMessage().contains("白班"));
        assertTrue(ex.getMessage().contains("只能留一条"));
    }

    @Test
    void nightReviewGuardRequiresDayShiftReading() {
        ProductionLine l = line(3L);
        LocalDate today = LocalDate.now();

        // 当天没有白班抄录 → 夜班复核不能提交
        when(meterReadingRepository.findByLineIdAndReadingDateAndShift(3L, today, MeterShift.DAY))
                .thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> meterReadingService.assertDayShiftReadingExists(l, today));
        assertTrue(ex.getMessage().contains("白班电表抄录"));
        assertTrue(ex.getMessage().contains("夜班复核不能提交"));

        // 当天已有白班抄录 → 放行
        when(meterReadingRepository.findByLineIdAndReadingDateAndShift(3L, today, MeterShift.DAY))
                .thenReturn(Optional.of(previous("5200.00")));
        assertDoesNotThrow(() -> meterReadingService.assertDayShiftReadingExists(l, today));
    }

    private ProductionLine line(Long id) {
        ProductionLine line = new ProductionLine();
        line.setId(id);
        line.setLineCode("LINE-00" + id);
        line.setLineName("装配一号线");
        return line;
    }

    private MeterReadingRequest request(Long lineId, MeterShift shift, String value) {
        MeterReadingRequest request = new MeterReadingRequest();
        request.setLineId(lineId);
        request.setShift(shift);
        request.setReadingValue(new BigDecimal(value));
        request.setReader("张电工");
        return request;
    }

    private MeterReading previous(String value) {
        MeterReading reading = new MeterReading();
        reading.setLineId(1L);
        reading.setReadingValue(new BigDecimal(value));
        return reading;
    }
}
