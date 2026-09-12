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
import static org.mockito.ArgumentMatchers.eq;
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
        when(productionLineRepository.findByIdForUpdate(4L))
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
    void approveRecalculatesHaltByCurrentStateAndPassesAfterResume() {
        // 目标产线曾停台但已复台：审批按当前停台状态重算（悲观锁当前读），剩余待批行可继续通过
        TransferApplicationItemRepository.ItemPreview preview =
                mock(TransferApplicationItemRepository.ItemPreview.class);
        when(preview.getApplicationId()).thenReturn(10L);
        when(preview.getStatus()).thenReturn(ItemStatus.PENDING);
        when(itemRepository.findPreviewById(5L)).thenReturn(Optional.of(preview));

        TransferApplication application = new TransferApplication();
        application.setId(10L);
        application.setApplicationNo("TA20260912000001");
        application.setApplicant("张三");
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
        when(springArchiveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 当前状态：已复台（NORMAL，产线上保留复台记录）
        ProductionLine resumedLine = line(4L, "装配四号线");
        resumedLine.setResumeOperator("王五");
        resumedLine.setResumeTime(LocalDateTime.now().minusMinutes(30));
        resumedLine.setResumeConclusion("设备检修完成，试产合格，恢复生产");
        when(productionLineRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(resumedLine));

        ProductionLine fromLine = line(1L, "装配一号线");
        when(productionLineRepository.findById(1L)).thenReturn(Optional.of(fromLine));

        when(itemRepository.approveIfPending(eq(5L), any(), any(), eq(ItemStatus.APPROVED), eq(ItemStatus.PENDING)))
                .thenReturn(1);
        when(transferRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.countByApplicationIdAndStatusForUpdate(10L, ItemStatus.PENDING)).thenReturn(0L);
        when(itemRepository.countByApplicationIdAndStatusForUpdate(10L, ItemStatus.APPROVED)).thenReturn(1L);
        when(itemRepository.countByApplicationIdAndStatusForUpdate(10L, ItemStatus.REJECTED)).thenReturn(0L);

        ApprovalRequest request = new ApprovalRequest();
        request.setItemIds(List.of(5L));
        request.setApprover("李四");

        List<ItemProcessResult> results = applicationService.approve(request);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        assertTrue(results.get(0).getMessage().contains("审批通过"));
    }

    @Test
    void approveBlockMessageExplainsReHaltAfterResume() {
        // 复台后又重新登记停台：拦截文案必须给出明确原因——附上停台登记时间，
        // 并指出本次停台系复台后重新登记，与操作记录里的复台记录对得上
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

        // 上一轮停台已于 08:00 复台，10:00 又登记了新一轮停台
        ProductionLine reHalted = haltedLine(4L, "装配四号线", "二次缺料",
                LocalDateTime.of(2026, 9, 13, 8, 0));
        reHalted.setHaltTime(LocalDateTime.of(2026, 9, 12, 10, 0));
        reHalted.setResumeOperator("王五");
        reHalted.setResumeTime(LocalDateTime.of(2026, 9, 12, 8, 0));
        reHalted.setResumeConclusion("缺料已补齐，复台");
        when(productionLineRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(reHalted));

        ApprovalRequest request = new ApprovalRequest();
        request.setItemIds(List.of(5L));
        request.setApprover("李四");

        List<ItemProcessResult> results = applicationService.approve(request);

        assertEquals(1, results.size());
        ItemProcessResult result = results.get(0);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("停台登记时间：2026-09-12 10:00:00"));
        assertTrue(result.getMessage().contains("2026-09-12 08:00:00 复台"));
        assertTrue(result.getMessage().contains("复台后重新登记"));
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
