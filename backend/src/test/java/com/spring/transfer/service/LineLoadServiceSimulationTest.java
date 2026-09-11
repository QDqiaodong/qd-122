package com.spring.transfer.service;

import com.spring.transfer.dto.LineSimulationEstimate;
import com.spring.transfer.dto.LoadStatus;
import com.spring.transfer.dto.SimulationEstimateResponse;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.entity.SpringArchive;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.SpringArchiveRepository;
import com.spring.transfer.repository.TransferRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 工序调拨模拟预估计算的单测：验证划出/划入后各产线承载率、系数越界与状态流转。
 */
@ExtendWith(MockitoExtension.class)
class LineLoadServiceSimulationTest {

    @Mock
    private ProductionLineRepository productionLineRepository;
    @Mock
    private SpringArchiveRepository springArchiveRepository;
    @Mock
    private TransferRecordRepository transferRecordRepository;
    @Mock
    private LoadAlertService loadAlertService;

    private LineLoadService lineLoadService;

    private List<ProductionLine> lines;
    private List<SpringArchive> springs;

    @BeforeEach
    void setUp() {
        lineLoadService = new LineLoadService(productionLineRepository, springArchiveRepository,
                transferRecordRepository, loadAlertService);

        lines = List.of(
                line(1L, "LINE-001", "装配一号线", 2, "0.2", "1.0"),
                line(2L, "LINE-002", "装配二号线", 4, "2.0", "3.0"),
                line(3L, "LINE-003", "装配三号线", 3, "4.0", "6.0"),
                line(4L, "LINE-004", "装配四号线", 5, "0.5", "3.5"));
        springs = List.of(
                spring(1L, "SP-2024-0001", "0.5", 1L),
                spring(2L, "SP-2024-0002", "1.2", 1L),
                spring(6L, "SP-2024-0006", "0.8", 1L),
                spring(3L, "SP-2024-0003", "2.5", 2L),
                spring(4L, "SP-2024-0004", "3.8", 2L),
                spring(9L, "SP-2024-0009", "0.6", 2L),
                spring(8L, "SP-2024-0008", "3.0", 4L),
                spring(10L, "SP-2024-0010", "2.0", 4L),
                spring(12L, "SP-2024-0012", "1.0", 4L));

        lenient().when(productionLineRepository.findAll()).thenReturn(lines);
        lenient().when(springArchiveRepository.findAll()).thenReturn(springs);
        lenient().when(transferRecordRepository.findByOperateTimeAfter(any())).thenReturn(List.of());
    }

    @Test
    void simulateTransferComputesPostTransferLoadForAffectedLines() {
        when(productionLineRepository.findById(4L)).thenReturn(Optional.of(lines.get(3)));

        // 将 SP-0001（一号线，0.5）与 SP-0003（二号线，2.5）模拟划入四号线
        SimulationEstimateResponse response = lineLoadService.simulateTransfer(List.of(1L, 3L), 4L);

        assertEquals(4L, response.getToLineId());
        assertEquals("装配四号线", response.getToLineName());
        assertEquals(2, response.getSpringCount());
        assertEquals(3, response.getLines().size());

        // 接收产线排最前：四号线 3 → 5，负载率 60% → 100%，正常 → 预警
        LineSimulationEstimate target = response.getLines().get(0);
        assertEquals(4L, target.getLineId());
        assertEquals("IN", target.getDirection());
        assertEquals(2, target.getMoveInCount());
        assertEquals(0, target.getMoveOutCount());
        assertEquals(3, target.getCurrentCount());
        assertEquals(5, target.getSimulatedCount());
        assertEquals(0, new BigDecimal("60.00").compareTo(target.getCurrentLoadRate()));
        assertEquals(0, new BigDecimal("100.00").compareTo(target.getSimulatedLoadRate()));
        assertEquals(LoadStatus.NORMAL.name(), target.getCurrentStatus());
        assertEquals(LoadStatus.WARNING.name(), target.getSimulatedStatus());
        assertFalse(target.getReasons().isEmpty());

        // 一号线 3 → 2，超载 → 预警（达到阈值），SP-0002 系数越界仍在
        LineSimulationEstimate line1 = response.getLines().get(1);
        assertEquals(1L, line1.getLineId());
        assertEquals("OUT", line1.getDirection());
        assertEquals(1, line1.getMoveOutCount());
        assertEquals(3, line1.getCurrentCount());
        assertEquals(2, line1.getSimulatedCount());
        assertEquals(LoadStatus.OVERLOAD.name(), line1.getCurrentStatus());
        assertEquals(LoadStatus.WARNING.name(), line1.getSimulatedStatus());
        assertEquals(1, line1.getSimulatedOutOfRangeCount());

        // 二号线 3 → 2，两件系数越界弹簧仍在
        LineSimulationEstimate line2 = response.getLines().get(2);
        assertEquals(2L, line2.getLineId());
        assertEquals("OUT", line2.getDirection());
        assertEquals(2, line2.getSimulatedCount());
        assertEquals(2, line2.getSimulatedOutOfRangeCount());
        assertEquals(LoadStatus.WARNING.name(), line2.getSimulatedStatus());
    }

    @Test
    void simulateTransferFlagsOutOfRangeOnTargetLine() {
        when(productionLineRepository.findById(3L)).thenReturn(Optional.of(lines.get(2)));

        // SP-0001 系数 0.5 超出三号线适用区间 4.0~6.0
        SimulationEstimateResponse response = lineLoadService.simulateTransfer(List.of(1L), 3L);

        LineSimulationEstimate target = response.getLines().get(0);
        assertEquals(3L, target.getLineId());
        assertEquals(1, target.getSimulatedOutOfRangeCount());
        assertEquals(LoadStatus.WARNING.name(), target.getSimulatedStatus());
        assertTrue(target.getReasons().stream().anyMatch(r -> r.contains("弹力系数")));
    }

    @Test
    void simulateTransferRejectsSpringsAlreadyOnTargetLine() {
        when(productionLineRepository.findById(1L)).thenReturn(Optional.of(lines.get(0)));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> lineLoadService.simulateTransfer(List.of(1L), 1L));
        assertTrue(ex.getMessage().contains("无需划转"));
    }

    @Test
    void simulateTransferRejectsMissingSprings() {
        when(productionLineRepository.findById(4L)).thenReturn(Optional.of(lines.get(3)));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> lineLoadService.simulateTransfer(List.of(999L), 4L));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    private ProductionLine line(Long id, String code, String name, int threshold, String min, String max) {
        ProductionLine line = new ProductionLine();
        line.setId(id);
        line.setLineCode(code);
        line.setLineName(name);
        line.setDailyCapacityThreshold(threshold);
        line.setElasticMin(new BigDecimal(min));
        line.setElasticMax(new BigDecimal(max));
        return line;
    }

    private SpringArchive spring(Long id, String code, String coefficient, Long lineId) {
        SpringArchive spring = new SpringArchive();
        spring.setId(id);
        spring.setSpringCode(code);
        spring.setModel("M-" + code);
        spring.setElasticCoefficient(new BigDecimal(coefficient));
        spring.setOuterDiameter(new BigDecimal("10"));
        spring.setCurrentLineId(lineId);
        spring.setInitialLineId(lineId);
        return spring;
    }
}
