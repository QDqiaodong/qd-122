package com.spring.transfer.service;

import com.spring.transfer.common.ApplicationStatus;
import com.spring.transfer.common.ItemStatus;
import com.spring.transfer.common.LineHaltStatus;
import com.spring.transfer.common.SealStatus;
import com.spring.transfer.dto.ApprovalRequest;
import com.spring.transfer.dto.ItemProcessResult;
import com.spring.transfer.dto.LineLoadBoardResponse;
import com.spring.transfer.dto.UrgentRequest;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.entity.SpringArchive;
import com.spring.transfer.entity.TransferApplication;
import com.spring.transfer.entity.TransferApplicationItem;
import com.spring.transfer.entity.TransferApplicationLog;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.SpringArchiveRepository;
import com.spring.transfer.repository.TransferApplicationItemRepository;
import com.spring.transfer.repository.TransferApplicationLogRepository;
import com.spring.transfer.repository.TransferApplicationRepository;
import com.spring.transfer.repository.TransferRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 划转申请加急单测：
 * 调度员可对待审批（含部分处理）申请标记加急并写原因，取消加急须写说明；
 * 重复加急、结案后加急、未加急取消均给出明确原因；
 * 申请单结案时加急标记自动解除并留痕；看板「加急待批数」按加急申请单的剩余待审批明细行计数，
 * 与审批台逐行口径一致，并对加急标记残留/表头状态漂移等对不齐场景给出明确原因。
 */
@ExtendWith(MockitoExtension.class)
class TransferApplicationUrgentTest {

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
    @Mock
    private NightLoadReviewService nightLoadReviewService;
    @Mock
    private ElasticSampleService elasticSampleService;

    private TransferApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new TransferApplicationService(
                applicationRepository, itemRepository, logRepository, springArchiveRepository,
                productionLineRepository, transferRecordRepository, transactionManager,
                lineLoadService, loadAlertService, nightLoadReviewService, elasticSampleService);
        lenient().when(productionLineRepository.findAll()).thenReturn(List.of());
        lenient().when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void markUrgentSetsFieldsAndWritesLog() {
        TransferApplication application = application(10L, ApplicationStatus.PENDING);
        when(applicationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(application));

        TransferApplication result = applicationService.markUrgent(10L, urgentRequest("王调度", "客户催单，需优先划转"));

        assertTrue(result.isUrgent());
        assertEquals("客户催单，需优先划转", result.getUrgentReason());
        assertEquals("王调度", result.getUrgentOperator());
        assertNotNull(result.getUrgentTime());

        TransferApplicationLog log = captureLastLog();
        assertEquals("URGENT", log.getAction());
        assertEquals("王调度", log.getOperator());
        assertTrue(log.getDetail().contains("客户催单，需优先划转"));
    }

    @Test
    void markUrgentAllowsPartialApplication() {
        // 部分处理（仍有待审批明细）的申请单属于待审批范畴，允许加急
        TransferApplication application = application(10L, ApplicationStatus.PARTIAL);
        when(applicationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(application));

        TransferApplication result = applicationService.markUrgent(10L, urgentRequest("王调度", "产线待料，加急处理"));

        assertTrue(result.isUrgent());
    }

    @Test
    void markUrgentRejectsDuplicateWithCurrentUrgentInfo() {
        TransferApplication application = application(10L, ApplicationStatus.PENDING);
        application.setUrgent(true);
        application.setUrgentOperator("李调度");
        application.setUrgentReason("已有加急原因");
        application.setUrgentTime(LocalDateTime.of(2026, 9, 12, 8, 30));
        when(applicationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(application));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> applicationService.markUrgent(10L, urgentRequest("王调度", "再次加急")));

        // 重复加急须给出明确原因：已加急、原加急人、请勿重复加急
        assertTrue(ex.getMessage().contains("已标记加急"));
        assertTrue(ex.getMessage().contains("李调度"));
        assertTrue(ex.getMessage().contains("2026-09-12 08:30"));
        assertTrue(ex.getMessage().contains("请勿重复加急"));
    }

    @Test
    void markUrgentRejectsClosedApplicationWithClearReason() {
        TransferApplication approved = application(10L, ApplicationStatus.APPROVED);
        when(applicationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(approved));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> applicationService.markUrgent(10L, urgentRequest("王调度", "结案后加急")));
        assertTrue(ex.getMessage().contains("已结案"));
        assertTrue(ex.getMessage().contains("全部通过"));
        assertTrue(ex.getMessage().contains("不能再标记加急"));

        TransferApplication rejected = application(11L, ApplicationStatus.REJECTED);
        when(applicationRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(rejected));

        RuntimeException ex2 = assertThrows(RuntimeException.class,
                () -> applicationService.markUrgent(11L, urgentRequest("王调度", "结案后加急")));
        assertTrue(ex2.getMessage().contains("已结案"));
        assertTrue(ex2.getMessage().contains("全部驳回"));
    }

    @Test
    void markUrgentRequiresReason() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> applicationService.markUrgent(10L, urgentRequest("王调度", "  ")));
        assertTrue(ex.getMessage().contains("必须填写加急原因"));
    }

    @Test
    void cancelUrgentClearsFieldsAndWritesLog() {
        TransferApplication application = application(10L, ApplicationStatus.PENDING);
        application.setUrgent(true);
        application.setUrgentOperator("李调度");
        application.setUrgentReason("客户催单");
        application.setUrgentTime(LocalDateTime.now().minusHours(1));
        when(applicationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(application));

        TransferApplication result = applicationService.cancelUrgent(10L, urgentRequest("王调度", "交期已协调，无需加急"));

        assertFalse(result.isUrgent());
        assertNull(result.getUrgentReason());
        assertNull(result.getUrgentOperator());
        assertNull(result.getUrgentTime());

        TransferApplicationLog log = captureLastLog();
        assertEquals("URGENT_CANCEL", log.getAction());
        assertEquals("王调度", log.getOperator());
        assertTrue(log.getDetail().contains("交期已协调，无需加急"));
    }

    @Test
    void cancelUrgentRequiresExplanation() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> applicationService.cancelUrgent(10L, urgentRequest("王调度", "")));
        assertTrue(ex.getMessage().contains("必须填写取消说明"));
    }

    @Test
    void cancelUrgentRejectsNonUrgentApplication() {
        TransferApplication application = application(10L, ApplicationStatus.PENDING);
        when(applicationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(application));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> applicationService.cancelUrgent(10L, urgentRequest("王调度", "取消说明")));
        assertTrue(ex.getMessage().contains("未标记加急"));
    }

    @Test
    void urgentFlagAutoClearsWhenApplicationCloses() {
        // 加急申请单的最后一条待审批明细审批通过 → 申请单结案，加急标记自动解除并留痕
        TransferApplicationItemRepository.ItemPreview preview =
                mock(TransferApplicationItemRepository.ItemPreview.class);
        when(preview.getApplicationId()).thenReturn(10L);
        when(preview.getStatus()).thenReturn(ItemStatus.PENDING);
        when(itemRepository.findPreviewById(5L)).thenReturn(Optional.of(preview));

        TransferApplication application = application(10L, ApplicationStatus.PARTIAL);
        application.setUrgent(true);
        when(applicationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(application));

        TransferApplicationItem item = new TransferApplicationItem();
        item.setId(5L);
        item.setApplicationId(10L);
        item.setSpringId(1L);
        item.setSpringCode("SP-2024-0001");
        item.setToLineId(2L);
        item.setToLineName("装配二号线");
        item.setStatus(ItemStatus.PENDING);
        when(itemRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(item));

        SpringArchive spring = spring(1L, "SP-2024-0001");
        when(springArchiveRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(spring));
        when(springArchiveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductionLine fromLine = line(1L, "装配一号线");
        ProductionLine toLine = line(2L, "装配二号线");
        when(productionLineRepository.findById(1L)).thenReturn(Optional.of(fromLine));
        when(productionLineRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(toLine));

        when(itemRepository.approveIfPending(eq(5L), any(), any(), eq(ItemStatus.APPROVED), eq(ItemStatus.PENDING)))
                .thenReturn(1);
        when(transferRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 结案汇总：待审批 0、通过 1、驳回 0 → 全部通过
        when(itemRepository.countByApplicationIdAndStatusForUpdate(10L, ItemStatus.PENDING)).thenReturn(0L);
        when(itemRepository.countByApplicationIdAndStatusForUpdate(10L, ItemStatus.APPROVED)).thenReturn(1L);
        when(itemRepository.countByApplicationIdAndStatusForUpdate(10L, ItemStatus.REJECTED)).thenReturn(0L);
        when(applicationRepository.clearUrgentIfMarked(10L)).thenReturn(1);

        ApprovalRequest request = new ApprovalRequest();
        request.setItemIds(List.of(5L));
        request.setApprover("李四");

        List<ItemProcessResult> results = applicationService.approve(request);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        // 结案后加急标记被清除，并写入 SYSTEM 自动解除日志
        verify(applicationRepository).clearUrgentIfMarked(10L);
        TransferApplicationLog log = captureLastLog();
        assertEquals("URGENT_CANCEL", log.getAction());
        assertEquals("SYSTEM", log.getOperator());
        assertTrue(log.getDetail().contains("结案"));
        assertTrue(log.getDetail().contains("加急标记自动解除"));
    }

    @Test
    void urgentFlagKeptWhileApplicationStillPending() {
        // 部分处理后仍有待审批明细 → 未结案，加急标记保留
        TransferApplicationItemRepository.ItemPreview preview =
                mock(TransferApplicationItemRepository.ItemPreview.class);
        when(preview.getApplicationId()).thenReturn(10L);
        when(preview.getStatus()).thenReturn(ItemStatus.PENDING);
        when(itemRepository.findPreviewById(5L)).thenReturn(Optional.of(preview));

        TransferApplication application = application(10L, ApplicationStatus.PENDING);
        application.setUrgent(true);
        when(applicationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(application));

        TransferApplicationItem item = new TransferApplicationItem();
        item.setId(5L);
        item.setApplicationId(10L);
        item.setSpringId(1L);
        item.setSpringCode("SP-2024-0001");
        item.setToLineId(2L);
        item.setToLineName("装配二号线");
        item.setStatus(ItemStatus.PENDING);
        when(itemRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(item));

        SpringArchive spring = spring(1L, "SP-2024-0001");
        when(springArchiveRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(spring));
        when(springArchiveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductionLine fromLine = line(1L, "装配一号线");
        ProductionLine toLine = line(2L, "装配二号线");
        when(productionLineRepository.findById(1L)).thenReturn(Optional.of(fromLine));
        when(productionLineRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(toLine));

        when(itemRepository.approveIfPending(eq(5L), any(), any(), eq(ItemStatus.APPROVED), eq(ItemStatus.PENDING)))
                .thenReturn(1);
        when(transferRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 汇总后仍有 1 条待审批 → 部分处理，未结案
        when(itemRepository.countByApplicationIdAndStatusForUpdate(10L, ItemStatus.PENDING)).thenReturn(1L);
        when(itemRepository.countByApplicationIdAndStatusForUpdate(10L, ItemStatus.APPROVED)).thenReturn(1L);
        when(itemRepository.countByApplicationIdAndStatusForUpdate(10L, ItemStatus.REJECTED)).thenReturn(0L);

        ApprovalRequest request = new ApprovalRequest();
        request.setItemIds(List.of(5L));
        request.setApprover("李四");

        applicationService.approve(request);

        verify(applicationRepository, never()).clearUrgentIfMarked(any());
    }

    @Test
    void boardCountsUrgentPendingByRemainingItemRows() {
        // 看板「加急待批」与审批台同口径：按加急申请单的剩余待审批明细行计数。
        // 加急单A剩2行待批、部分处理加急单B剩3行待批 → 共 5 行 / 2 张加急单；
        // 已处理行不再占用加急名额，计数对齐无异常原因
        LineLoadService boardService = new LineLoadService(
                productionLineRepository, springArchiveRepository, transferRecordRepository,
                applicationRepository, loadAlertService);
        when(springArchiveRepository.findAll()).thenReturn(List.of());
        when(transferRecordRepository.findByOperateTimeAfter(any())).thenReturn(List.of());
        when(loadAlertService.attachOpenEvents(anyList())).thenReturn(Map.of("pending", 0, "open", 0));
        // 先构造投影 mock 列表再传入 thenReturn，避免外层 when 未完成时嵌套 stub 触发 UnfinishedStubbing
        var urgentStats = List.of(
                urgentStat(10L, "TA20260912000100", ApplicationStatus.PENDING, 2L),
                urgentStat(11L, "TA20260912000101", ApplicationStatus.PARTIAL, 3L));
        when(applicationRepository.findUrgentPendingStats(ItemStatus.PENDING)).thenReturn(urgentStats);

        LineLoadBoardResponse board = boardService.getBoard();

        assertEquals(5, board.getUrgentPendingCount());
        assertEquals(2, board.getUrgentPendingApplicationCount());
        assertEquals(0, board.getUrgentStaleCount());
        assertTrue(board.getUrgentPendingAligned());
        assertTrue(board.getUrgentPendingMismatchReasons().isEmpty());
    }

    @Test
    void boardReportsStaleUrgentFlagWhenNoPendingItemRemains() {
        // 异常场景：申请单已结案且加急标记未自动解除（仍 urgent=true、剩余待批 0 行）。
        // 剩余待批行口径下该单不再占用加急名额（计 0 行），同时必须给出明确对不齐原因
        LineLoadService boardService = new LineLoadService(
                productionLineRepository, springArchiveRepository, transferRecordRepository,
                applicationRepository, loadAlertService);
        when(springArchiveRepository.findAll()).thenReturn(List.of());
        when(transferRecordRepository.findByOperateTimeAfter(any())).thenReturn(List.of());
        when(loadAlertService.attachOpenEvents(anyList())).thenReturn(Map.of("pending", 0, "open", 0));
        // 先构造投影 mock 列表再传入 thenReturn，避免外层 when 未完成时嵌套 stub 触发 UnfinishedStubbing
        var urgentStats = List.of(
                urgentStat(20L, "TA20260912000102", ApplicationStatus.APPROVED, 0L));
        when(applicationRepository.findUrgentPendingStats(ItemStatus.PENDING)).thenReturn(urgentStats);

        LineLoadBoardResponse board = boardService.getBoard();

        assertEquals(0, board.getUrgentPendingCount());
        assertEquals(0, board.getUrgentPendingApplicationCount());
        assertEquals(1, board.getUrgentStaleCount());
        assertFalse(board.getUrgentPendingAligned());
        assertEquals(1, board.getUrgentPendingMismatchReasons().size());
        String reason = board.getUrgentPendingMismatchReasons().get(0);
        assertTrue(reason.contains("加急标记"));
        assertTrue(reason.contains("20"));
    }

    @Test
    void boardReportsHeaderStatusDriftWhenPendingItemsExistOnClosedApplication() {
        // 异常场景：表头已「全部通过」却仍有 2 行待批明细（表头状态与明细进度漂移）。
        // 看板仍按剩余待批行计入（与审批台逐行看到的量一致），并明确指出状态需校正
        LineLoadService boardService = new LineLoadService(
                productionLineRepository, springArchiveRepository, transferRecordRepository,
                applicationRepository, loadAlertService);
        when(springArchiveRepository.findAll()).thenReturn(List.of());
        when(transferRecordRepository.findByOperateTimeAfter(any())).thenReturn(List.of());
        when(loadAlertService.attachOpenEvents(anyList())).thenReturn(Map.of("pending", 0, "open", 0));
        // 先构造投影 mock 列表再传入 thenReturn，避免外层 when 未完成时嵌套 stub 触发 UnfinishedStubbing
        var urgentStats = List.of(
                urgentStat(30L, "TA20260912000103", ApplicationStatus.APPROVED, 2L));
        when(applicationRepository.findUrgentPendingStats(ItemStatus.PENDING)).thenReturn(urgentStats);

        LineLoadBoardResponse board = boardService.getBoard();

        assertEquals(2, board.getUrgentPendingCount());
        assertEquals(1, board.getUrgentPendingApplicationCount());
        assertFalse(board.getUrgentPendingAligned());
        String reason = board.getUrgentPendingMismatchReasons().get(0);
        assertTrue(reason.contains("已结案"));
        assertTrue(reason.contains("30"));
    }

    private TransferApplicationRepository.UrgentPendingStat urgentStat(
            Long applicationId, String applicationNo, ApplicationStatus status, long pendingItemCount) {
        TransferApplicationRepository.UrgentPendingStat stat =
                mock(TransferApplicationRepository.UrgentPendingStat.class);
        // 投影方法按各用例场景只用到其中一部分（如 stale 场景不读 status），统一 lenient
        lenient().when(stat.getApplicationId()).thenReturn(applicationId);
        lenient().when(stat.getApplicationNo()).thenReturn(applicationNo);
        lenient().when(stat.getStatus()).thenReturn(status);
        lenient().when(stat.getPendingItemCount()).thenReturn(pendingItemCount);
        return stat;
    }

    private TransferApplicationLog captureLastLog() {
        ArgumentCaptor<TransferApplicationLog> captor = ArgumentCaptor.forClass(TransferApplicationLog.class);
        verify(logRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private UrgentRequest urgentRequest(String operator, String reason) {
        UrgentRequest request = new UrgentRequest();
        request.setOperator(operator);
        request.setReason(reason);
        return request;
    }

    private TransferApplication application(Long id, ApplicationStatus status) {
        TransferApplication application = new TransferApplication();
        application.setId(id);
        application.setApplicationNo("TA2026091200010" + id);
        application.setApplicant("张三");
        application.setToLineId(2L);
        application.setToLineName("装配二号线");
        application.setReason("工序重构");
        application.setStatus(status);
        return application;
    }

    private ProductionLine line(Long id, String name) {
        ProductionLine line = new ProductionLine();
        line.setId(id);
        line.setLineCode("LINE-00" + id);
        line.setLineName(name);
        line.setHaltStatus(LineHaltStatus.NORMAL);
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
