package com.spring.transfer.service;

import com.spring.transfer.common.SealStatus;
import com.spring.transfer.dto.FlagCountersignRequest;
import com.spring.transfer.dto.SealRequest;
import com.spring.transfer.dto.UnsealRequest;
import com.spring.transfer.entity.SpringArchive;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.SpringArchiveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 弹簧封存/解封单测：登记封存原因与预计解封日、重复封存防护、解封必须填结论、
 * 解封后恢复可划转状态。
 */
@ExtendWith(MockitoExtension.class)
class SpringArchiveServiceSealTest {

    @Mock
    private SpringArchiveRepository springArchiveRepository;
    @Mock
    private ProductionLineRepository productionLineRepository;
    @Mock
    private ElasticSpecCacheService elasticSpecCacheService;
    @Mock
    private ElasticSampleService elasticSampleService;

    private SpringArchiveService springArchiveService;

    @BeforeEach
    void setUp() {
        springArchiveService = new SpringArchiveService(
                springArchiveRepository, productionLineRepository, elasticSpecCacheService, elasticSampleService);
        lenient().when(springArchiveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(productionLineRepository.findAllById(any())).thenReturn(List.of());
    }

    @Test
    void sealRegistersReasonAndExpectedUnsealDate() {
        SpringArchive spring = spring(SealStatus.NONE);
        when(springArchiveRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(spring));

        SealRequest request = new SealRequest();
        request.setOperator("质量员王五");
        request.setReason("抽检不合格，弹力系数复测偏差超限");
        request.setExpectedUnsealDate(LocalDate.now().plusDays(7));

        SpringArchive result = springArchiveService.seal(1L, request);

        assertEquals(SealStatus.SEALED, result.getSealStatus());
        assertTrue(result.isSealed());
        assertEquals("抽检不合格，弹力系数复测偏差超限", result.getSealReason());
        assertEquals(LocalDate.now().plusDays(7), result.getSealExpectedUnsealDate());
        assertEquals("质量员王五", result.getSealOperator());
        assertNotNull(result.getSealTime());
    }

    @Test
    void sealRejectsAlreadySealedSpring() {
        SpringArchive spring = spring(SealStatus.SEALED);
        spring.setSealReason("待复测");
        when(springArchiveRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(spring));

        SealRequest request = new SealRequest();
        request.setOperator("质量员王五");
        request.setReason("再次封存");
        request.setExpectedUnsealDate(LocalDate.now().plusDays(3));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> springArchiveService.seal(1L, request));
        assertTrue(ex.getMessage().contains("已处于封存状态"));
    }

    @Test
    void sealRejectsPastExpectedUnsealDate() {
        SpringArchive spring = spring(SealStatus.NONE);
        when(springArchiveRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(spring));

        SealRequest request = new SealRequest();
        request.setOperator("质量员王五");
        request.setReason("待复测");
        request.setExpectedUnsealDate(LocalDate.now().minusDays(1));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> springArchiveService.seal(1L, request));
        assertTrue(ex.getMessage().contains("预计解封日不能早于今天"));
    }

    @Test
    void unsealClearsStatusAndRecordsConclusion() {
        SpringArchive spring = spring(SealStatus.SEALED);
        spring.setSealReason("待复测");
        spring.setSealExpectedUnsealDate(LocalDate.now().plusDays(5));
        when(springArchiveRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(spring));

        UnsealRequest request = new UnsealRequest();
        request.setOperator("质量员王五");
        request.setConclusion("复测合格，恢复使用");

        SpringArchive result = springArchiveService.unseal(1L, request);

        assertEquals(SealStatus.NONE, result.getSealStatus());
        assertFalse(result.isSealed());
        assertEquals("复测合格，恢复使用", result.getUnsealConclusion());
        assertEquals("质量员王五", result.getUnsealOperator());
        assertNotNull(result.getUnsealTime());
        // 封存信息保留作为最近一次封存记录，便于追溯
        assertEquals("待复测", result.getSealReason());
    }

    @Test
    void unsealRejectsSpringNotSealed() {
        SpringArchive spring = spring(SealStatus.NONE);
        when(springArchiveRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(spring));

        UnsealRequest request = new UnsealRequest();
        request.setOperator("质量员王五");
        request.setConclusion("复测合格");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> springArchiveService.unseal(1L, request));
        assertTrue(ex.getMessage().contains("未处于封存状态"));
    }

    @Test
    void sealSummaryContainsReasonAndExpectedDate() {
        SpringArchive spring = spring(SealStatus.SEALED);
        spring.setSealReason("抽检不合格");
        spring.setSealExpectedUnsealDate(LocalDate.of(2026, 9, 20));

        String summary = spring.getSealSummary();
        assertTrue(summary.contains("SP-2024-0001"));
        assertTrue(summary.contains("抽检不合格"));
        assertTrue(summary.contains("2026-09-20"));
    }

    @Test
    void countersignAfterAllClosedRecordsOperatorIdAndNoteAndClearsYellowFlag() {
        SpringArchive spring = spring(SealStatus.NONE);
        when(springArchiveRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(spring));
        // 模拟实时重算：加签前为「已闭环待加签」(open=1, pending=0)，加签后黄标摘除
        AtomicInteger invocation = new AtomicInteger();
        doAnswer(inv -> {
            // 参数是 List<SpringArchive>
            @SuppressWarnings("unchecked")
            List<SpringArchive> targets = inv.getArgument(0);
            if (invocation.incrementAndGet() == 1) {
                targets.get(0).setOpenDeviationCount(1);
                targets.get(0).setPendingDeviationCount(0);
                targets.get(0).setYellowFlag(true);
            } else {
                targets.get(0).setOpenDeviationCount(0);
                targets.get(0).setPendingDeviationCount(0);
                targets.get(0).setYellowFlag(false);
            }
            return null;
        }).when(elasticSampleService).markYellowFlags(any());

        FlagCountersignRequest request = new FlagCountersignRequest();
        request.setOperatorId("QZ-007");
        request.setNote("偏离留样均已闭环复测合格，质量主管确认摘标");

        SpringArchive result = springArchiveService.countersignFlag(1L, request);

        assertEquals("QZ-007", result.getFlagCountersignOperator());
        assertEquals("偏离留样均已闭环复测合格，质量主管确认摘标", result.getFlagCountersignNote());
        assertNotNull(result.getFlagCountersignTime());
        assertTrue(result.isFlagCountersigned());
        assertFalse(result.isYellowFlagged(), "加签完成后黄标摘除，恢复可勾选划转");
    }

    @Test
    void countersignRejectedWhileDeviationStillPending() {
        SpringArchive spring = spring(SealStatus.NONE);
        when(springArchiveRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(spring));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<SpringArchive> targets = inv.getArgument(0);
            targets.get(0).setOpenDeviationCount(2);
            targets.get(0).setPendingDeviationCount(1);
            targets.get(0).setYellowFlag(true);
            return null;
        }).when(elasticSampleService).markYellowFlags(any());

        FlagCountersignRequest request = new FlagCountersignRequest();
        request.setOperatorId("QZ-007");
        request.setNote("尝试提前摘标");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> springArchiveService.countersignFlag(1L, request));
        assertTrue(ex.getMessage().contains("待闭环"));
        assertTrue(ex.getMessage().contains("才能摘标加签"));
        assertNull(spring.getFlagCountersignTime());
    }

    @Test
    void countersignRejectedWhenNoYellowFlag() {
        SpringArchive spring = spring(SealStatus.NONE);
        when(springArchiveRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(spring));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<SpringArchive> targets = inv.getArgument(0);
            targets.get(0).setOpenDeviationCount(0);
            targets.get(0).setPendingDeviationCount(0);
            targets.get(0).setYellowFlag(false);
            return null;
        }).when(elasticSampleService).markYellowFlags(any());

        FlagCountersignRequest request = new FlagCountersignRequest();
        request.setOperatorId("QZ-007");
        request.setNote("无标可摘");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> springArchiveService.countersignFlag(1L, request));
        assertTrue(ex.getMessage().contains("无黄标"));
    }

    private SpringArchive spring(SealStatus status) {
        SpringArchive spring = new SpringArchive();
        spring.setId(1L);
        spring.setSpringCode("SP-2024-0001");
        spring.setModel("C-Spring-05");
        spring.setElasticCoefficient(new BigDecimal("0.5"));
        spring.setOuterDiameter(new BigDecimal("12.5"));
        spring.setCurrentLineId(1L);
        spring.setInitialLineId(1L);
        spring.setSealStatus(status);
        return spring;
    }
}
