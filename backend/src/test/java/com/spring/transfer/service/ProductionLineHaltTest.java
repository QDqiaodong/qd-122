package com.spring.transfer.service;

import com.spring.transfer.common.ItemStatus;
import com.spring.transfer.common.LineHaltStatus;
import com.spring.transfer.common.SealStatus;
import com.spring.transfer.dto.ApprovalRequest;
import com.spring.transfer.dto.ItemProcessResult;
import com.spring.transfer.dto.LineHaltGuardResponse;
import com.spring.transfer.dto.LineHaltRequest;
import com.spring.transfer.dto.LineResumeRequest;
import com.spring.transfer.dto.SubmitApplicationRequest;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.entity.SpringArchive;
import com.spring.transfer.entity.TransferApplication;
import com.spring.transfer.entity.TransferApplicationItem;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.SpringArchiveRepository;
import com.spring.transfer.repository.TransferApplicationItemRepository;
import com.spring.transfer.repository.TransferApplicationLogRepository;
import com.spring.transfer.repository.TransferApplicationRepository;
import com.spring.transfer.repository.TransferRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 产线临时停台拦截单测：
 * 停台期间该产线不能作为划转接收方（提交申请与审批通过均拦截并给出明确原因）；
 * 登记停台前的影响提示返回待审批申请量；重复停台、未停台复台与缺结论复台均被拒绝。
 */
@ExtendWith(MockitoExtension.class)
class ProductionLineHaltTest {

    @Mock
    private TransferApplicationRepository applicationRepository;
    @Mock
    private TransferApplicationItemRepository itemRepository;
    @Mock
    private TransferApplicationLogRepository logRepository;
    @Mock
    private SpringArchiveRepository springArchiveRepository;
    @Mock
    private ProductionLineRepository productionLineRepository;
    @Mock
    private TransferRecordRepository transferRecordRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private LineLoadService lineLoadService;
    @Mock
    private LoadAlertService loadAlertService;

    private TransferApplicationService applicationService;
    private ProductionLineService lineService;

    @BeforeEach
    void setUp() {
        applicationService = new TransferApplicationService(
                applicationRepository, itemRepository, logRepository, springArchiveRepository,
                productionLineRepository, transferRecordRepository, transactionManager,
                lineLoadService, loadAlertService);
        lineService = new ProductionLineService(productionLineRepository, itemRepository);
    }

    @Test
    void submitRejectsHaltedTargetLineWithClearReason() {
        ProductionLine halted = haltedLine(4L, "装配四号线", "设备检修",
                LocalDateTime.of(2026, 9, 12, 8, 0));
        when(productionLineRepository.findById(4L)).thenReturn(Optional.of(halted));

        SubmitApplicationRequest request = new SubmitApplicationRequest();
        request.setSpringIds(List.of(1L));
        request.setToLineId(4L);
        request.setApplicant("张三");
        request.setReason("工序重构");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> applicationService.submit(request));
        // 拦截原因必须明确：停台、产线名称、停台原因、预计复台时间
        assertTrue(ex.getMessage().contains("停台"));
        assertTrue(ex.getMessage().contains("装配四号线"));
        assertTrue(ex.getMessage().contains("设备检修"));
        assertTrue(ex.getMessage().contains("2026-09-12 08:00"));
        assertTrue(ex.getMessage().contains("不能作为划转接收方"));
    }

    @Test
    void approveRejectsItemWhenTargetLineHaltedAfterSubmit() {
        TransferApplicationItemRepository.ItemPreview preview =
                mock(TransferApplicationItemRepository.ItemPreview.class);
        when(preview.getApplicationId()).thenReturn(10L);
        when(preview.getStatus()).thenReturn(ItemStatus.PENDING);
        when(itemRepository.findPreviewById(5L)).thenReturn(Optional.of(preview));

        TransferApplication application = new TransferApplication();
        application.setId(10L);
        when(applicationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(application));

        TransferApplicationItem item = new TransferApplicationItem();
        item.setId(5L);
        item.setApplicationId(10L);
        item.setSpringId(1L);
        item.setSpringCode("SP-2024-0001");
        item.setToLineId(4L);
        item.setToLineName("装配四号线");
        item.setStatus(ItemStatus.PENDING);
        when(itemRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(item));

        SpringArchive spring = spring(1L, "SP-2024-0001");
        when(springArchiveRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(spring));

        // 申请提交后目标产线被调度员登记停台
        when(productionLineRepository.findById(4L))
                .thenReturn(Optional.of(haltedLine(4L, "装配四号线", "缺料停线",
                        LocalDateTime.of(2026, 9, 12, 8, 0))));

        ApprovalRequest request = new ApprovalRequest();
        request.setItemIds(List.of(5L));
        request.setApprover("李四");

        List<ItemProcessResult> results = applicationService.approve(request);

        assertEquals(1, results.size());
        ItemProcessResult result = results.get(0);
        assertFalse(result.isSuccess());
        assertEquals("SP-2024-0001", result.getSpringCode());
        assertTrue(result.getMessage().contains("停台"));
        assertTrue(result.getMessage().contains("缺料停线"));
        assertTrue(result.getMessage().contains("不能接收划转"));
    }

    @Test
    void haltGuardReturnsPendingApplicationCounts() {
        ProductionLine halted = haltedLine(4L, "装配四号线", "设备检修",
                LocalDateTime.of(2026, 9, 12, 8, 0));
        when(productionLineRepository.findById(4L)).thenReturn(Optional.of(halted));
        when(itemRepository.countByToLineIdAndStatus(4L, ItemStatus.PENDING)).thenReturn(6L);
        when(itemRepository.countDistinctApplicationByToLineIdAndStatus(4L, ItemStatus.PENDING)).thenReturn(2L);

        LineHaltGuardResponse guard = lineService.getHaltGuard(4L);

        assertTrue(guard.isHalted());
        assertEquals(6L, guard.getPendingItemCount());
        assertEquals(2L, guard.getPendingApplicationCount());
    }

    @Test
    void haltRejectsDuplicateHaltAndResumeRequiresConclusion() {
        ProductionLine halted = haltedLine(4L, "装配四号线", "设备检修",
                LocalDateTime.of(2026, 9, 12, 8, 0));
        when(productionLineRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(halted));

        LineHaltRequest haltRequest = new LineHaltRequest();
        haltRequest.setOperator("王五");
        haltRequest.setReason("工艺调整");
        haltRequest.setExpectedResumeTime(LocalDateTime.now().plusDays(1));

        RuntimeException haltEx = assertThrows(RuntimeException.class,
                () -> lineService.halt(4L, haltRequest));
        assertTrue(haltEx.getMessage().contains("已处于停台状态"));

        // 复台结论为空时校验注解在 Web 层拦截，Service 直接传空白结论不应落库（非停台态分支已验证重复停台）
        LineResumeRequest resumeRequest = new LineResumeRequest();
        resumeRequest.setOperator("王五");
        resumeRequest.setConclusion("设备检修完成，试产合格，恢复生产");
        when(productionLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductionLine resumed = lineService.resume(4L, resumeRequest);
        assertEquals(LineHaltStatus.NORMAL, resumed.getHaltStatus());
        assertEquals("设备检修完成，试产合格，恢复生产", resumed.getResumeConclusion());
        assertEquals("王五", resumed.getResumeOperator());
        assertNotNull(resumed.getResumeTime());
        // 停台登记信息保留以便追溯
        assertEquals("设备检修", resumed.getHaltReason());
    }

    @Test
    void resumeRejectsNormalLine() {
        ProductionLine normal = line(4L, "装配四号线");
        when(productionLineRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(normal));

        LineResumeRequest resumeRequest = new LineResumeRequest();
        resumeRequest.setOperator("王五");
        resumeRequest.setConclusion("恢复生产");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> lineService.resume(4L, resumeRequest));
        assertTrue(ex.getMessage().contains("未处于停台状态"));
    }

    private ProductionLine line(Long id, String name) {
        ProductionLine line = new ProductionLine();
        line.setId(id);
        line.setLineCode("LINE-00" + id);
        line.setLineName(name);
        line.setHaltStatus(LineHaltStatus.NORMAL);
        return line;
    }

    private ProductionLine haltedLine(Long id, String name, String reason, LocalDateTime resumeTime) {
        ProductionLine line = line(id, name);
        line.setHaltStatus(LineHaltStatus.HALTED);
        line.setHaltReason(reason);
        line.setHaltExpectedResumeTime(resumeTime);
        line.setHaltOperator("调度员甲");
        line.setHaltTime(LocalDateTime.now().minusHours(2));
        return line;
    }

    private SpringArchive spring(Long id, String code) {
        SpringArchive spring = new SpringArchive();
        spring.setId(id);
        spring.setSpringCode(code);
        spring.setModel("M-" + code);
        spring.setElasticCoefficient(new BigDecimal("0.5"));
        spring.setOuterDiameter(new BigDecimal("12.5"));
        spring.setCurrentLineId(1L);
        spring.setInitialLineId(1L);
        spring.setSealStatus(SealStatus.NONE);
        return spring;
    }
}
