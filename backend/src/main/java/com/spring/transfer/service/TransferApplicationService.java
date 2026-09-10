package com.spring.transfer.service;

import com.spring.transfer.common.ApplicationStatus;
import com.spring.transfer.common.ItemStatus;
import com.spring.transfer.dto.ApplicationDetailResponse;
import com.spring.transfer.dto.ApprovalRequest;
import com.spring.transfer.dto.ItemProcessResult;
import com.spring.transfer.dto.SubmitApplicationRequest;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.entity.SpringArchive;
import com.spring.transfer.entity.TransferApplication;
import com.spring.transfer.entity.TransferApplicationItem;
import com.spring.transfer.entity.TransferApplicationLog;
import com.spring.transfer.entity.TransferRecord;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.SpringArchiveRepository;
import com.spring.transfer.repository.TransferApplicationItemRepository;
import com.spring.transfer.repository.TransferApplicationLogRepository;
import com.spring.transfer.repository.TransferApplicationRepository;
import com.spring.transfer.repository.TransferRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferApplicationService {
    private final TransferApplicationRepository applicationRepository;
    private final TransferApplicationItemRepository itemRepository;
    private final TransferApplicationLogRepository logRepository;
    private final SpringArchiveRepository springArchiveRepository;
    private final ProductionLineRepository productionLineRepository;
    private final TransferRecordRepository transferRecordRepository;
    private final PlatformTransactionManager transactionManager;

    public Page<TransferApplication> findAll(ApplicationStatus status, String keyword, Pageable pageable) {
        Page<TransferApplication> page = applicationRepository.findByCondition(status, keyword, pageable);
        page.getContent().forEach(this::fillItemCounts);
        return page;
    }

    public ApplicationDetailResponse getDetail(Long id) {
        TransferApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("划转申请不存在"));
        fillItemCounts(application);
        List<TransferApplicationItem> items = itemRepository.findByApplicationIdOrderByIdAsc(id);
        List<TransferApplicationLog> logs = logRepository.findByApplicationIdOrderByOperateTimeAscIdAsc(id);
        return new ApplicationDetailResponse(application, items, logs);
    }

    /**
     * 提交划转申请：仅创建申请单与明细，审批通过前不改变弹簧归属
     */
    @Transactional
    public TransferApplication submit(SubmitApplicationRequest request) {
        ProductionLine toLine = productionLineRepository.findById(request.getToLineId())
                .orElseThrow(() -> new RuntimeException("目标产线不存在"));

        List<Long> springIds = request.getSpringIds().stream().distinct().toList();
        // 悲观锁锁定弹簧档案，防止并发提交对同一弹簧重复申请
        List<SpringArchive> springs = springArchiveRepository.findAllByIdInForUpdate(springIds);
        if (springs.size() != springIds.size()) {
            Set<Long> foundIds = springs.stream().map(SpringArchive::getId).collect(Collectors.toSet());
            List<Long> missing = springIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new RuntimeException("以下弹簧档案不存在，ID: " + missing);
        }

        // 同产线校验
        List<String> sameLineCodes = springs.stream()
                .filter(s -> s.getCurrentLineId().equals(request.getToLineId()))
                .map(SpringArchive::getSpringCode)
                .toList();
        if (!sameLineCodes.isEmpty()) {
            throw new RuntimeException("以下弹簧已归属目标产线「" + toLine.getLineName() + "」，无需划转: "
                    + String.join(", ", sameLineCodes));
        }

        // 重复提交校验：同一弹簧存在待审批明细时不允许再次申请
        List<TransferApplicationItem> pendingItems = itemRepository.findBySpringIdInAndStatus(springIds, ItemStatus.PENDING);
        if (!pendingItems.isEmpty()) {
            String codes = pendingItems.stream().map(TransferApplicationItem::getSpringCode).distinct().sorted()
                    .collect(Collectors.joining(", "));
            throw new RuntimeException("以下弹簧已存在待审批的划转申请，请勿重复提交: " + codes);
        }

        Map<Long, ProductionLine> lineCache = productionLineRepository.findAll().stream()
                .collect(Collectors.toMap(ProductionLine::getId, Function.identity()));

        TransferApplication application = new TransferApplication();
        application.setApplicationNo(generateApplicationNo());
        application.setApplicant(request.getApplicant().trim());
        application.setToLineId(toLine.getId());
        application.setToLineName(toLine.getLineName());
        application.setReason(request.getReason().trim());
        application.setStatus(ApplicationStatus.PENDING);
        application = applicationRepository.save(application);

        List<TransferApplicationItem> items = new ArrayList<>();
        for (SpringArchive spring : springs) {
            ProductionLine fromLine = lineCache.get(spring.getCurrentLineId());
            if (fromLine == null) {
                throw new RuntimeException("弹簧 " + spring.getSpringCode() + " 的当前产线不存在");
            }
            TransferApplicationItem item = new TransferApplicationItem();
            item.setApplicationId(application.getId());
            item.setSpringId(spring.getId());
            item.setSpringCode(spring.getSpringCode());
            item.setModel(spring.getModel());
            item.setElasticCoefficient(spring.getElasticCoefficient());
            item.setOuterDiameter(spring.getOuterDiameter());
            item.setFromLineId(fromLine.getId());
            item.setFromLineName(fromLine.getLineName());
            item.setToLineId(toLine.getId());
            item.setToLineName(toLine.getLineName());
            item.setStatus(ItemStatus.PENDING);
            items.add(itemRepository.save(item));
        }

        logRepository.save(new TransferApplicationLog(application.getId(), "SUBMIT",
                application.getApplicant(),
                "提交划转申请，目标产线「" + toLine.getLineName() + "」，共 " + items.size() + " 条弹簧，申请原因：" + application.getReason()));

        fillItemCounts(application);
        return application;
    }

    /**
     * 批量审批通过：每条明细独立事务处理，单条失败不影响其他明细
     */
    public List<ItemProcessResult> approve(ApprovalRequest request) {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        List<ItemProcessResult> results = new ArrayList<>();
        for (Long itemId : request.getItemIds().stream().distinct().toList()) {
            results.add(txTemplate.execute(status -> doApprove(itemId, request.getApprover().trim())));
        }
        return results;
    }

    /**
     * 批量驳回：每条明细独立事务处理，驳回必须填写原因
     */
    public List<ItemProcessResult> reject(ApprovalRequest request) {
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new RuntimeException("驳回时必须填写驳回原因");
        }
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        List<ItemProcessResult> results = new ArrayList<>();
        for (Long itemId : request.getItemIds().stream().distinct().toList()) {
            results.add(txTemplate.execute(status -> doReject(itemId, request.getApprover().trim(), request.getReason().trim())));
        }
        return results;
    }

    /** 单条明细审批通过，由 approve() 在独立事务中调用 */
    private ItemProcessResult doApprove(Long itemId, String approver) {
        TransferApplicationItem item = itemRepository.findById(itemId).orElse(null);
        if (item == null) {
            return ItemProcessResult.fail(itemId, null, "申请明细不存在");
        }
        if (item.getStatus() != ItemStatus.PENDING) {
            return ItemProcessResult.fail(itemId, item.getSpringCode(),
                    "该申请已由 " + item.getApprover() + " 处理（" + statusText(item.getStatus()) + "），请勿重复操作");
        }

        TransferApplication application = applicationRepository.findById(item.getApplicationId())
                .orElseThrow(() -> new RuntimeException("划转申请不存在"));

        // 悲观锁锁定弹簧，串行化并发审批对同一弹簧的归属变更
        SpringArchive spring = springArchiveRepository.findByIdForUpdate(item.getSpringId()).orElse(null);
        if (spring == null) {
            return ItemProcessResult.fail(itemId, item.getSpringCode(), "弹簧档案不存在");
        }
        if (spring.getCurrentLineId().equals(item.getToLineId())) {
            return ItemProcessResult.fail(itemId, item.getSpringCode(),
                    "弹簧当前已归属目标产线「" + item.getToLineName() + "」，无需划转，请驳回该申请");
        }

        // 原子状态流转，防止并发审批重复生效
        int updated = itemRepository.approveIfPending(itemId, approver, LocalDateTime.now(),
                ItemStatus.APPROVED, ItemStatus.PENDING);
        if (updated == 0) {
            return ItemProcessResult.fail(itemId, item.getSpringCode(), "该申请已被其他审批人处理，请刷新查看最新状态");
        }

        ProductionLine fromLine = productionLineRepository.findById(spring.getCurrentLineId())
                .orElseThrow(() -> new RuntimeException("弹簧 " + spring.getSpringCode() + " 的当前产线不存在"));

        // 审批通过后才更新弹簧归属并生成划转流水
        spring.setCurrentLineId(item.getToLineId());
        springArchiveRepository.save(spring);

        TransferRecord record = new TransferRecord();
        record.setSpringId(spring.getId());
        record.setSpringCode(spring.getSpringCode());
        record.setFromLineId(fromLine.getId());
        record.setFromLineName(fromLine.getLineName());
        record.setToLineId(item.getToLineId());
        record.setToLineName(item.getToLineName());
        record.setOperator(approver);
        record.setOperateTime(LocalDateTime.now());
        record.setRemark("划转申请单 " + application.getApplicationNo() + " 审批通过（申请人：" + application.getApplicant() + "）");
        record = transferRecordRepository.save(record);

        itemRepository.updateTransferRecordId(itemId, record.getId());

        logRepository.save(new TransferApplicationLog(item.getApplicationId(), "APPROVE", approver,
                "审批通过：" + item.getSpringCode() + "，" + fromLine.getLineName() + " → " + item.getToLineName()
                        + "，已生成划转流水 #" + record.getId()));

        refreshApplicationStatus(item.getApplicationId());
        return ItemProcessResult.success(itemId, item.getSpringCode(),
                "审批通过，弹簧已划转至「" + item.getToLineName() + "」");
    }

    /** 单条明细驳回，由 reject() 在独立事务中调用 */
    private ItemProcessResult doReject(Long itemId, String approver, String reason) {
        TransferApplicationItem item = itemRepository.findById(itemId).orElse(null);
        if (item == null) {
            return ItemProcessResult.fail(itemId, null, "申请明细不存在");
        }
        if (item.getStatus() != ItemStatus.PENDING) {
            return ItemProcessResult.fail(itemId, item.getSpringCode(),
                    "该申请已由 " + item.getApprover() + " 处理（" + statusText(item.getStatus()) + "），请勿重复操作");
        }

        int updated = itemRepository.rejectIfPending(itemId, approver, LocalDateTime.now(), reason,
                ItemStatus.REJECTED, ItemStatus.PENDING);
        if (updated == 0) {
            return ItemProcessResult.fail(itemId, item.getSpringCode(), "该申请已被其他审批人处理，请刷新查看最新状态");
        }

        logRepository.save(new TransferApplicationLog(item.getApplicationId(), "REJECT", approver,
                "审批驳回：" + item.getSpringCode() + "，驳回原因：" + reason));

        refreshApplicationStatus(item.getApplicationId());
        return ItemProcessResult.success(itemId, item.getSpringCode(), "已驳回");
    }

    /**
     * 根据明细处理进度汇总申请单状态
     */
    private void refreshApplicationStatus(Long applicationId) {
        long pending = itemRepository.countByApplicationIdAndStatus(applicationId, ItemStatus.PENDING);
        long approved = itemRepository.countByApplicationIdAndStatus(applicationId, ItemStatus.APPROVED);
        long rejected = itemRepository.countByApplicationIdAndStatus(applicationId, ItemStatus.REJECTED);
        long total = pending + approved + rejected;

        ApplicationStatus status;
        if (pending == total) {
            status = ApplicationStatus.PENDING;
        } else if (approved == total) {
            status = ApplicationStatus.APPROVED;
        } else if (rejected == total) {
            status = ApplicationStatus.REJECTED;
        } else {
            status = ApplicationStatus.PARTIAL;
        }
        applicationRepository.updateStatus(applicationId, status);
    }

    private void fillItemCounts(TransferApplication application) {
        long pending = itemRepository.countByApplicationIdAndStatus(application.getId(), ItemStatus.PENDING);
        long approved = itemRepository.countByApplicationIdAndStatus(application.getId(), ItemStatus.APPROVED);
        long rejected = itemRepository.countByApplicationIdAndStatus(application.getId(), ItemStatus.REJECTED);
        application.setPendingCount((int) pending);
        application.setApprovedCount((int) approved);
        application.setRejectedCount((int) rejected);
        application.setTotalCount((int) (pending + approved + rejected));
    }

    private String generateApplicationNo() {
        String no;
        do {
            no = "TA" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        } while (applicationRepository.existsByApplicationNo(no));
        return no;
    }

    private String statusText(ItemStatus status) {
        return switch (status) {
            case PENDING -> "待审批";
            case APPROVED -> "已通过";
            case REJECTED -> "已驳回";
        };
    }
}
