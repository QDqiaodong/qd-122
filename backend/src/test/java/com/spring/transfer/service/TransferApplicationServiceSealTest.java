package com.spring.transfer.service;

import com.spring.transfer.common.ItemStatus;
import com.spring.transfer.common.SealStatus;
import com.spring.transfer.dto.ApprovalRequest;
import com.spring.transfer.dto.ItemProcessResult;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 封存拦截单测：封存中的弹簧不能进入划转申请（提交拦截并给出明确原因），
 * 申请提交后才被封存的弹簧在审批环节同样被拦截。
 */
@ExtendWith(MockitoExtension.class)
class TransferApplicationServiceSealTest {

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
    }

    @Test
    void submitRejectsSealedSpringsWithClearReason() {
        ProductionLine toLine = line(4L, "装配四号线");
        when(productionLineRepository.findById(4L)).thenReturn(Optional.of(toLine));

        SpringArchive sealed = spring(1L, "SP-2024-0001");
        sealed.setSealStatus(SealStatus.SEALED);
        sealed.setSealReason("待复测，等待实验室结果");
        sealed.setSealExpectedUnsealDate(LocalDate.of(2026, 9, 20));
        SpringArchive normal = spring(2L, "SP-2024-0002");
        when(springArchiveRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(sealed, normal));
        when(itemRepository.findBySpringIdInAndStatus(any(), eq(ItemStatus.PENDING))).thenReturn(List.of());

        SubmitApplicationRequest request = new SubmitApplicationRequest();
        request.setSpringIds(List.of(1L, 2L));
        request.setToLineId(4L);
        request.setApplicant("张三");
        request.setReason("工序重构");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> applicationService.submit(request));
        // 拦截原因必须明确：封存状态、弹簧编号、封存原因、预计解封日
        assertTrue(ex.getMessage().contains("封存"));
        assertTrue(ex.getMessage().contains("SP-2024-0001"));
        assertTrue(ex.getMessage().contains("待复测，等待实验室结果"));
        assertTrue(ex.getMessage().contains("2026-09-20"));
        // 正常弹簧不应出现在拦截原因中
        assertFalse(ex.getMessage().contains("SP-2024-0002"));
    }

    @Test
    void approveRejectsItemWhenSpringSealedAfterSubmit() {
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

        // 申请提交后弹簧被质量员封存
        SpringArchive sealed = spring(1L, "SP-2024-0001");
        sealed.setSealStatus(SealStatus.SEALED);
        sealed.setSealReason("抽检不合格");
        sealed.setSealExpectedUnsealDate(LocalDate.of(2026, 9, 20));
        when(springArchiveRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(sealed));

        ApprovalRequest request = new ApprovalRequest();
        request.setItemIds(List.of(5L));
        request.setApprover("李四");

        List<ItemProcessResult> results = applicationService.approve(request);

        assertEquals(1, results.size());
        ItemProcessResult result = results.get(0);
        assertFalse(result.isSuccess());
        assertEquals("SP-2024-0001", result.getSpringCode());
        assertTrue(result.getMessage().contains("封存"));
        assertTrue(result.getMessage().contains("抽检不合格"));
    }

    private ProductionLine line(Long id, String name) {
        ProductionLine line = new ProductionLine();
        line.setId(id);
        line.setLineCode("LINE-00" + id);
        line.setLineName(name);
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
        return spring;
    }
}
