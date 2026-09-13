package com.spring.transfer.service;

import com.spring.transfer.dto.LineInspectionRequest;
import com.spring.transfer.entity.LineInspection;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.repository.LineInspectionRepository;
import com.spring.transfer.repository.ProductionLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 开班点检单测：
 * 登记时按标准气源压力区间（0.40~0.80 MPa）与工装完好情况判定是否通过；
 * 调拨模拟接收方守卫对 从未点检/当日未点检/当日点检未通过 给出明确拦截原因，
 * 当日点检通过的产线放行。
 */
@ExtendWith(MockitoExtension.class)
class LineInspectionServiceTest {

    @Mock
    private LineInspectionRepository inspectionRepository;
    @Mock
    private ProductionLineRepository productionLineRepository;

    private LineInspectionService inspectionService;

    @BeforeEach
    void setUp() {
        inspectionService = new LineInspectionService(inspectionRepository, productionLineRepository);
    }

    @Test
    void registerPassesWhenToolingIntactAndPressureInRange() {
        when(productionLineRepository.findById(1L)).thenReturn(Optional.of(line(1L)));
        when(inspectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LineInspection saved = inspectionService.register(request(1L, "0.60", true));

        assertTrue(saved.getPassed());
        assertEquals("王质检", saved.getInspector());
        assertEquals("装配一号线", saved.getLineName());
        assertNotNull(saved.getInspectTime());
        assertTrue(saved.isToday());
    }

    @Test
    void registerFailsWhenToolingBrokenOrPressureOutOfRange() {
        when(productionLineRepository.findById(1L)).thenReturn(Optional.of(line(1L)));
        when(inspectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 工装不完好 → 不通过（即使压力在区间内）
        assertFalse(inspectionService.register(request(1L, "0.60", false)).getPassed());
        // 气源压力低于标准区间 → 不通过
        assertFalse(inspectionService.register(request(1L, "0.30", true)).getPassed());
        // 气源压力高于标准区间 → 不通过
        assertFalse(inspectionService.register(request(1L, "0.90", true)).getPassed());
        // 边界值：恰好等于上下限 → 通过
        assertTrue(inspectionService.register(request(1L, "0.40", true)).getPassed());
        assertTrue(inspectionService.register(request(1L, "0.80", true)).getPassed());
    }

    @Test
    void receiveBlockReasonRejectsNeverInspectedLine() {
        ProductionLine line = line(4L);
        line.setLineName("装配四号线");
        when(inspectionRepository.findTopByLineIdOrderByInspectTimeDescIdDesc(4L))
                .thenReturn(Optional.empty());

        String reason = inspectionService.receiveBlockReason(line);

        assertNotNull(reason);
        assertTrue(reason.contains("未开班点检"));
        assertTrue(reason.contains("装配四号线"));
        assertTrue(reason.contains("不能进行调拨模拟"));
    }

    @Test
    void receiveBlockReasonRejectsLineInspectedOnlyYesterday() {
        // 最近一次点检在昨天：按当日口径视为未开班点检
        ProductionLine line = line(4L);
        line.setLineName("装配四号线");
        LineInspection yesterday = inspection(4L, "0.60", true, true,
                LocalDateTime.now().minusDays(1));
        when(inspectionRepository.findTopByLineIdOrderByInspectTimeDescIdDesc(4L))
                .thenReturn(Optional.of(yesterday));

        String reason = inspectionService.receiveBlockReason(line);

        assertNotNull(reason);
        assertTrue(reason.contains("未开班点检"));
    }

    @Test
    void receiveBlockReasonRejectsLineFailedTodayWithDetail() {
        ProductionLine line = line(4L);
        line.setLineName("装配四号线");
        LineInspection failed = inspection(4L, "0.30", true, false, LocalDateTime.now().minusHours(1));
        when(inspectionRepository.findTopByLineIdOrderByInspectTimeDescIdDesc(4L))
                .thenReturn(Optional.of(failed));

        String reason = inspectionService.receiveBlockReason(line);

        assertNotNull(reason);
        assertTrue(reason.contains("开班点检未通过"));
        assertTrue(reason.contains("装配四号线"));
        // 拦截原因必须给出点检结果明细：气源压力与标准区间、工装情况、点检人
        assertTrue(reason.contains("0.3"));
        assertTrue(reason.contains("0.4~0.8"));
        assertTrue(reason.contains("工装完好"));
        assertTrue(reason.contains("王质检"));
        assertTrue(reason.contains("不能作为划转接收方"));
    }

    @Test
    void receiveBlockReasonPassesLineInspectedToday() {
        ProductionLine line = line(4L);
        LineInspection passed = inspection(4L, "0.60", true, true, LocalDateTime.now().minusHours(1));
        when(inspectionRepository.findTopByLineIdOrderByInspectTimeDescIdDesc(4L))
                .thenReturn(Optional.of(passed));

        assertNull(inspectionService.receiveBlockReason(line));
    }

    private ProductionLine line(Long id) {
        ProductionLine line = new ProductionLine();
        line.setId(id);
        line.setLineCode("LINE-00" + id);
        line.setLineName("装配一号线");
        return line;
    }

    private LineInspectionRequest request(Long lineId, String pressure, boolean toolingIntact) {
        LineInspectionRequest request = new LineInspectionRequest();
        request.setLineId(lineId);
        request.setAirPressure(new BigDecimal(pressure));
        request.setToolingIntact(toolingIntact);
        request.setInspector("王质检");
        return request;
    }

    private LineInspection inspection(Long lineId, String pressure, boolean toolingIntact,
                                      boolean passed, LocalDateTime inspectTime) {
        LineInspection inspection = new LineInspection();
        inspection.setLineId(lineId);
        inspection.setLineCode("LINE-00" + lineId);
        inspection.setLineName("装配四号线");
        inspection.setAirPressure(new BigDecimal(pressure));
        inspection.setToolingIntact(toolingIntact);
        inspection.setInspector("王质检");
        inspection.setPassed(passed);
        inspection.setInspectTime(inspectTime);
        return inspection;
    }
}
