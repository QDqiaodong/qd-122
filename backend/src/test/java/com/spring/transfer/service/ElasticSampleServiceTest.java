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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 弹力抽检留样单测：
 * 按产线登记实测系数并按该线适用区间判定偏离；待闭环可改实测系数（偏离标记重算），
 * 已闭环不能改；处置结论必填才能闭环；黄标按「存在偏离且未闭环留样」实时挂接，
 * 黄标件不能勾进划转申请。
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
    void markYellowFlagsOnlyForSpringsWithOpenDeviationSamples() {
        SpringArchive s1 = spring(1L, "SP-2024-0001", 1L);
        SpringArchive s2 = spring(2L, "SP-2024-0002", 1L);
        when(sampleRepository.findBySpringIdInAndStatusAndDeviatedTrueOrderByIdAsc(any(), eq(SampleStatus.OPEN)))
                .thenReturn(List.of(sample(10L, 1L, "SP-2024-0001", "1.2", true, SampleStatus.OPEN)));

        service.markYellowFlags(List.of(s1, s2));

        assertTrue(s1.isYellowFlagged());
        assertEquals(1, s1.getOpenDeviationCount());
        assertFalse(s2.isYellowFlagged());
        assertEquals(0, s2.getOpenDeviationCount());
    }

    @Test
    void transferGuardBlocksYellowFlaggedSpringWithClearReason() {
        SpringArchive s1 = spring(1L, "SP-2024-0001", 1L);
        when(sampleRepository.findBySpringIdInAndStatusAndDeviatedTrueOrderByIdAsc(any(), eq(SampleStatus.OPEN)))
                .thenReturn(List.of(sample(10L, 1L, "SP-2024-0001", "1.2000", true, SampleStatus.OPEN)));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.assertNoOpenDeviation(List.of(s1)));
        assertTrue(ex.getMessage().contains("黄标"));
        assertTrue(ex.getMessage().contains("SP-2024-0001"));
        assertTrue(ex.getMessage().contains("ES"));
        assertTrue(ex.getMessage().contains("1.2000"));
        assertTrue(ex.getMessage().contains("不能进入划转申请"));
    }

    @Test
    void transferGuardPassesWhenNoOpenDeviation() {
        when(sampleRepository.findBySpringIdInAndStatusAndDeviatedTrueOrderByIdAsc(any(), eq(SampleStatus.OPEN)))
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
        return sample;
    }
}
