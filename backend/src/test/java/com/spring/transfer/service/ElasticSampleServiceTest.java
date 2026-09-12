package com.spring.transfer.service;

import com.spring.transfer.common.SampleStatus;
import com.spring.transfer.common.SealStatus;
import com.spring.transfer.dto.CloseSampleRequest;
import com.spring.transfer.dto.RegisterSampleRequest;
import com.spring.transfer.dto.UpdateMeasuredRequest;
import com.spring.transfer.entity.ElasticSample;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.entity.SpringArchive;
import com.spring.transfer.repository.ElasticSampleRepository;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.SpringArchiveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 弹力抽检留样单测：
 * 按产线登记实测系数并按该线适用区间判定偏离；待闭环可改实测系数（偏离标记重算），
 * 已闭环不能改；处置结论必填才能闭环；黄标按「存在未被摘标加签确认的偏离留样」实时挂接：
 * 闭环后不自动摘标，质量主管加签（加签时间晚于闭环时间）后才摘除；
 * 待闭环或已闭环待加签的黄标件都不能勾进划转申请。
 */
@ExtendWith(MockitoExtension.class)
class ElasticSampleServiceTest {

    @Mock
    private ElasticSampleRepository sampleRepository;
    @Mock
    private SpringArchiveRepository springArchiveRepository;
    @Mock
    private ProductionLineRepository productionLineRepository;

    private ElasticSampleService service;

    @BeforeEach
    void setUp() {
        service = new ElasticSampleService(sampleRepository, springArchiveRepository, productionLineRepository);
        lenient().when(sampleRepository.existsBySampleNo(any())).thenReturn(false);
        lenient().when(sampleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registerMarksDeviationAgainstRegisteredLineRange() {
        SpringArchive spring = spring(1L, "SP-2024-0001", 1L);
        when(springArchiveRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(spring));
        when(productionLineRepository.findById(1L)).thenReturn(Optional.of(line(1L, 0.2, 1.0)));

        RegisterSampleRequest request = new RegisterSampleRequest();
        request.setSpringId(1L);
        request.setMeasuredCoefficient(new BigDecimal("1.2000"));
        request.setOperator("质量员赵六");

        ElasticSample saved = service.register(request);

        assertTrue(saved.getDeviated(), "实测 1.2 超出上限 1.0，应判定偏离");
        assertEquals(SampleStatus.OPEN, saved.getStatus());
        assertEquals(0, new BigDecimal("0.2000").compareTo(saved.getLineElasticMin()));
        assertEquals(0, new BigDecimal("1.0000").compareTo(saved.getLineElasticMax()));
        assertEquals("装配一号线", saved.getLineName());
        assertNotNull(saved.getSampleNo());
        assertTrue(saved.getSampleNo().startsWith("ES"));
    }

    @Test
    void registerInRangeIsNotDeviated() {
        when(springArchiveRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(spring(1L, "SP-2024-0001", 1L)));
        when(productionLineRepository.findById(1L)).thenReturn(Optional.of(line(1L, 0.2, 1.0)));

        RegisterSampleRequest request = new RegisterSampleRequest();
        request.setSpringId(1L);
        request.setMeasuredCoefficient(new BigDecimal("0.5000"));
        request.setOperator("质量员赵六");

        assertFalse(service.register(request).getDeviated());
    }

    @Test
    void registerRejectsLineDifferentFromSpringCurrentLine() {
        when(springArchiveRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(spring(1L, "SP-2024-0001", 2L)));

        RegisterSampleRequest request = new RegisterSampleRequest();
        request.setSpringId(1L);
        request.setLineId(1L);
        request.setMeasuredCoefficient(new BigDecimal("0.9"));
        request.setOperator("质量员赵六");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.register(request));
        assertTrue(ex.getMessage().contains("当前归属产线与登记产线不一致"));
    }

    @Test
    void updateMeasuredRecomputesDeviationWhileOpen() {
        ElasticSample sample = sample(10L, 1L, "SP-2024-0001", "1.2000", true, SampleStatus.OPEN);
        when(sampleRepository.findById(10L)).thenReturn(Optional.of(sample));

        UpdateMeasuredRequest request = new UpdateMeasuredRequest();
        request.setMeasuredCoefficient(new BigDecimal("0.8000"));

        ElasticSample saved = service.updateMeasured(10L, request);
        assertFalse(saved.getDeviated(), "改回区间内后偏离标记应解除（黄标同步摘下）");
        assertEquals(0, new BigDecimal("0.8000").compareTo(saved.getMeasuredCoefficient()));
    }

    @Test
    void updateMeasuredRejectedAfterClosed() {
        when(sampleRepository.findById(10L))
                .thenReturn(Optional.of(sample(10L, 1L, "SP-2024-0001", "1.2000", true, SampleStatus.CLOSED)));

        UpdateMeasuredRequest request = new UpdateMeasuredRequest();
        request.setMeasuredCoefficient(new BigDecimal("0.8000"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.updateMeasured(10L, request));
        assertTrue(ex.getMessage().contains("已闭环"));
        assertTrue(ex.getMessage().contains("不能修改实测系数"));
    }

    @Test
    void closeRequiresConclusionAndLocksSample() {
        ElasticSample sample = sample(10L, 1L, "SP-2024-0001", "1.2000", true, SampleStatus.OPEN);
        when(sampleRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sample));

        CloseSampleRequest request = new CloseSampleRequest();
        request.setOperator("质量员赵六");
        request.setConclusion("复测确认超差，已通知工艺调整并安排返工");

        ElasticSample saved = service.close(10L, request);
        assertEquals(SampleStatus.CLOSED, saved.getStatus());
        assertEquals("复测确认超差，已通知工艺调整并安排返工", saved.getConclusion());
        assertNotNull(saved.getCloseTime());
        assertEquals("质量员赵六", saved.getCloseOperator());
    }

    @Test
    void closeTwiceRejected() {
        when(sampleRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(sample(10L, 1L, "SP-2024-0001", "1.2000", true, SampleStatus.CLOSED)));

        CloseSampleRequest request = new CloseSampleRequest();
        request.setOperator("质量员赵六");
        request.setConclusion("任何结论");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.close(10L, request));
        assertTrue(ex.getMessage().contains("已闭环"));
    }

    @Test
    void markYellowFlagsForSpringsWithPendingDeviationSamples() {
        SpringArchive s1 = spring(1L, "SP-2024-0001", 1L);
        SpringArchive s2 = spring(2L, "SP-2024-0002", 1L);
        when(sampleRepository.findBySpringIdInAndDeviatedTrueOrderByIdAsc(any()))
                .thenReturn(List.of(sample(10L, 1L, "SP-2024-0001", "1.2", true, SampleStatus.OPEN, null)));

        service.markYellowFlags(List.of(s1, s2));

        assertTrue(s1.isYellowFlagged());
        assertEquals(1, s1.getOpenDeviationCount());
        assertEquals(1, s1.getPendingDeviationCount());
        assertFalse(s2.isYellowFlagged());
        assertEquals(0, s2.getOpenDeviationCount());
    }

    @Test
    void closedDeviationKeepsYellowFlagUntilCountersigned() {
        SpringArchive s1 = spring(1L, "SP-2024-0001", 1L);
        LocalDateTime closeTime = LocalDateTime.now().minusHours(2);
        when(sampleRepository.findBySpringIdInAndDeviatedTrueOrderByIdAsc(any()))
                .thenReturn(List.of(sample(10L, 1L, "SP-2024-0001", "1.2", true, SampleStatus.CLOSED, closeTime)));

        // 未加签：闭环后黄标保持，且待闭环计数为 0（允许走摘标加签）
        service.markYellowFlags(List.of(s1));
        assertTrue(s1.isYellowFlagged(), "偏离留样已闭环但未摘标加签，黄标应保持");
        assertEquals(1, s1.getOpenDeviationCount());
        assertEquals(0, s1.getPendingDeviationCount());

        // 加签时间早于闭环时间：不能确认该闭环单（异常水位），黄标仍在
        s1.setFlagCountersignOperator("Q001");
        s1.setFlagCountersignNote("加签");
        s1.setFlagCountersignTime(closeTime.minusHours(1));
        service.markYellowFlags(List.of(s1));
        assertTrue(s1.isYellowFlagged());

        // 加签时间晚于闭环时间：黄标摘除，恢复可划转
        s1.setFlagCountersignTime(LocalDateTime.now());
        service.markYellowFlags(List.of(s1));
        assertFalse(s1.isYellowFlagged(), "质量主管摘标加签后黄标应摘除");
        assertEquals(0, s1.getOpenDeviationCount());
        assertEquals(0, s1.getPendingDeviationCount());
    }

    @Test
    void newDeviationAfterCountersignRaisesYellowFlagAgain() {
        SpringArchive s1 = spring(1L, "SP-2024-0001", 1L);
        LocalDateTime countersignTime = LocalDateTime.now().minusHours(4);
        s1.setFlagCountersignOperator("Q001");
        s1.setFlagCountersignNote("上一批偏离已闭环加签");
        s1.setFlagCountersignTime(countersignTime);

        ElasticSample closedOld = sample(10L, 1L, "SP-2024-0001", "1.2", true,
                SampleStatus.CLOSED, countersignTime.minusHours(2));
        ElasticSample openNew = sample(11L, 1L, "SP-2024-0001", "1.3", true,
                SampleStatus.OPEN, null);
        when(sampleRepository.findBySpringIdInAndDeviatedTrueOrderByIdAsc(any()))
                .thenReturn(List.of(closedOld, openNew));

        service.markYellowFlags(List.of(s1));

        assertTrue(s1.isYellowFlagged(), "加签后新登记的待闭环偏离留样应使黄标重新挂上");
        assertEquals(1, s1.getOpenDeviationCount(), "仅新单未被加签确认");
        assertEquals(1, s1.getPendingDeviationCount());
    }

    @Test
    void transferGuardBlocksYellowFlaggedSpringWithClearReason() {
        SpringArchive s1 = spring(1L, "SP-2024-0001", 1L);
        when(sampleRepository.findBySpringIdInAndDeviatedTrueOrderByIdAsc(any()))
                .thenReturn(List.of(sample(10L, 1L, "SP-2024-0001", "1.2000", true, SampleStatus.OPEN, null)));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.assertNoOpenDeviation(List.of(s1)));
        assertTrue(ex.getMessage().contains("黄标"));
        assertTrue(ex.getMessage().contains("SP-2024-0001"));
        assertTrue(ex.getMessage().contains("ES"));
        assertTrue(ex.getMessage().contains("1.2000"));
        assertTrue(ex.getMessage().contains("摘标加签"));
        assertTrue(ex.getMessage().contains("不能进入划转申请"));
    }

    @Test
    void transferGuardBlocksClosedButNotCountersignedSpring() {
        SpringArchive s1 = spring(1L, "SP-2024-0001", 1L);
        when(sampleRepository.findBySpringIdInAndDeviatedTrueOrderByIdAsc(any()))
                .thenReturn(List.of(sample(10L, 1L, "SP-2024-0001", "1.2000", true,
                        SampleStatus.CLOSED, LocalDateTime.now().minusHours(1))));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.assertNoOpenDeviation(List.of(s1)));
        assertTrue(ex.getMessage().contains("黄标"));
        assertTrue(ex.getMessage().contains("待质量主管摘标加签"));
    }

    @Test
    void transferGuardPassesAfterCountersign() {
        SpringArchive s1 = spring(1L, "SP-2024-0001", 1L);
        LocalDateTime closeTime = LocalDateTime.now().minusHours(2);
        s1.setFlagCountersignOperator("Q001");
        s1.setFlagCountersignNote("闭环复测合格，同意摘标");
        s1.setFlagCountersignTime(LocalDateTime.now());
        when(sampleRepository.findBySpringIdInAndDeviatedTrueOrderByIdAsc(any()))
                .thenReturn(List.of(sample(10L, 1L, "SP-2024-0001", "1.2000", true,
                        SampleStatus.CLOSED, closeTime)));

        assertDoesNotThrow(() -> service.assertNoOpenDeviation(List.of(s1)));
    }

    @Test
    void transferGuardPassesWhenNoOpenDeviation() {
        when(sampleRepository.findBySpringIdInAndDeviatedTrueOrderByIdAsc(any()))
                .thenReturn(List.of());
        assertDoesNotThrow(() -> service.assertNoOpenDeviation(
                List.of(spring(2L, "SP-2024-0002", 1L))));
    }

    private ProductionLine line(Long id, Double min, Double max) {
        ProductionLine line = new ProductionLine();
        line.setId(id);
        line.setLineCode("LINE-00" + id);
        line.setLineName("装配一号线");
        line.setElasticMin(BigDecimal.valueOf(min));
        line.setElasticMax(BigDecimal.valueOf(max));
        return line;
    }

    private SpringArchive spring(Long id, String code, Long currentLineId) {
        SpringArchive spring = new SpringArchive();
        spring.setId(id);
        spring.setSpringCode(code);
        spring.setModel("M-" + code);
        spring.setElasticCoefficient(new BigDecimal("0.5000"));
        spring.setOuterDiameter(new BigDecimal("12.5000"));
        spring.setCurrentLineId(currentLineId);
        spring.setInitialLineId(currentLineId);
        spring.setSealStatus(SealStatus.NONE);
        return spring;
    }

    private ElasticSample sample(Long id, Long springId, String springCode, String measured,
                                 boolean deviated, SampleStatus status) {
        return sample(id, springId, springCode, measured, deviated, status,
                status == SampleStatus.CLOSED ? LocalDateTime.now().minusHours(1) : null);
    }

    private ElasticSample sample(Long id, Long springId, String springCode, String measured,
                                 boolean deviated, SampleStatus status, LocalDateTime closeTime) {
        ElasticSample sample = new ElasticSample();
        sample.setId(id);
        sample.setSampleNo("ES20260912000" + id);
        sample.setSpringId(springId);
        sample.setSpringCode(springCode);
        sample.setModel("M-" + springCode);
        sample.setLineId(1L);
        sample.setLineCode("LINE-001");
        sample.setLineName("装配一号线");
        sample.setMeasuredCoefficient(new BigDecimal(measured));
        sample.setLineElasticMin(new BigDecimal("0.2000"));
        sample.setLineElasticMax(new BigDecimal("1.0000"));
        sample.setDeviated(deviated);
        sample.setStatus(status);
        sample.setOperator("质量员赵六");
        if (status == SampleStatus.CLOSED) {
            sample.setCloseOperator("质量员赵六");
            sample.setCloseTime(closeTime);
            sample.setConclusion("复测确认超差，安排返工");
        }
        return sample;
    }
}
