package com.spring.transfer.service;

import com.spring.transfer.common.ApplicationStatus;
import com.spring.transfer.common.ItemStatus;
import com.spring.transfer.common.LineHaltStatus;
import com.spring.transfer.dto.ApplicationDetailResponse;
import com.spring.transfer.dto.ApprovalRequest;
import com.spring.transfer.dto.ItemProcessResult;
import com.spring.transfer.dto.SubmitApplicationRequest;
import com.spring.transfer.dto.UrgentRequest;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TransferApplicationService {
    private final TransferApplicationRepository applicationRepository;
    private final TransferApplicationItemRepository itemRepository;
    private final TransferApplicationLogRepository logRepository;
    private final SpringArchiveRepository springArchiveRepository;
    private final ProductionLineRepository productionLineRepository;
    private final TransferRecordRepository transferRecordRepository;
    private final PlatformTransactionManager transactionManager;
    private final LineLoadService lineLoadService;
    private final LoadAlertService loadAlertService;

    public TransferApplicationService(TransferApplicationRepository applicationRepository,
                                      TransferApplicationItemRepository itemRepository,
                                      TransferApplicationLogRepository logRepository,
                                      SpringArchiveRepository springArchiveRepository,
                                      ProductionLineRepository productionLineRepository,
                                      TransferRecordRepository transferRecordRepository,
                                      PlatformTransactionManager transactionManager,
                                      @Lazy LineLoadService lineLoadService,
                                      @Lazy LoadAlertService loadAlertService) {
        this.applicationRepository = applicationRepository;
        this.itemRepository = itemRepository;
        this.logRepository = logRepository;
        this.springArchiveRepository = springArchiveRepository;
        this.productionLineRepository = productionLineRepository;
        this.transferRecordRepository = transferRecordRepository;
        this.transactionManager = transactionManager;
        this.lineLoadService = lineLoadService;
        this.loadAlertService = loadAlertService;
    }

    public Page<TransferApplication> findAll(ApplicationStatus status, String keyword, Boolean halted,
                                             Boolean urgent, Pageable pageable) {
        // 审批台按「目标产线是否停台」筛选：先取当前停台产线ID集合，再以IN条件过滤申请单；
        // 停台状态以数据库为准，页面刷新后标记保持
        List<Long> haltedLineIds = productionLineRepository.findByHaltStatus(LineHaltStatus.HALTED).stream()
                .map(ProductionLine::getId)
                .collect(Collectors.toList());
        int haltFilter;
        if (halted == null) {
            haltFilter = 0;
        } else {
            haltFilter = halted ? 1 : 2;
        }
        if (haltedLineIds.isEmpty()) {
            // 集合始终保持非空（haltFilter=0 时该条件不参与匹配），避免空集合 IN 与 null 集合参数问题
            haltedLineIds = List.of(-1L);
        }
        int urgentFilter;
        if (urgent == null) {
            urgentFilter = 0;
        } else {
            urgentFilter = urgent ? 1 : 2;
        }
        Page<TransferApplication> page = applicationRepository.findByCondition(
                status, keyword == null || keyword.isBlank() ? null : keyword.trim(),
                haltFilter, haltedLineIds, urgentFilter, pageable);
        page.getContent().forEach(this::fillItemCounts);
        enrichHaltMarkers(page.getContent());
        return page;
    }

    /** 挂接目标产线的实时停台标记与停台摘要，供审批台列表/详情给出明确拦截原因 */
    private void enrichHaltMarkers(List<TransferApplication> applications) {
        if (applications.isEmpty()) {
            return;
        }
        Map<Long, ProductionLine> lineCache = productionLineRepository.findAll().stream()
                .collect(Collectors.toMap(ProductionLine::getId, Function.identity()));
        applications.forEach(app -> {
            ProductionLine line = lineCache.get(app.getToLineId());
            if (line != null) {
                app.setToLineHalted(line.isHalted());
                app.setToLineHaltReason(line.getHaltReason());
                app.setToLineExpectedResumeTime(line.getHaltExpectedResumeTime());
            }
        });
    }

    public ApplicationDetailResponse getDetail(Long id) {
        TransferApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("划转申请不存在"));
        fillItemCounts(application);
        enrichHaltMarkers(List.of(application));
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
        // 停台校验：临时停台期间该产线不能作为划转接收方，提示中给出停台原因与预计复台时间
        if (toLine.isHalted()) {
            throw new RuntimeException(toLine.getHaltSummary()
                    + "，停台期间不能作为划转接收方，请待复台后再提交申请");
        }

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

        // 封存校验：封存中的弹簧（抽检不合格/待复测）不能进入划转申请，提示中给出封存原因与预计解封日
        List<SpringArchive> sealedSprings = springs.stream().filter(SpringArchive::isSealed).toList();
        if (!sealedSprings.isEmpty()) {
            String details = sealedSprings.stream().map(SpringArchive::getSealSummary)
                    .collect(Collectors.joining("；"));
            throw new RuntimeException("以下弹簧处于封存状态，封存期间不能进入划转申请，请先解封: " + details);
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
     * 标记加急：调度员对待审批（含部分处理）申请单标记加急并填写加急原因，
     * 审批台默认按加急优先排序展示。已结案（全部通过/全部驳回）单据不能再加急，
     * 重复加急被拒绝并回显当前加急信息。加急标记持久化，刷新后标记、排序与看板统计保持一致。
     */
    @Transactional
    public TransferApplication markUrgent(Long id, UrgentRequest request) {
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new RuntimeException("标记加急必须填写加急原因");
        }
        // 悲观锁串行化同一申请单的加急/取消加急/审批并发操作
        TransferApplication application = applicationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("划转申请不存在"));
        if (application.getStatus() == ApplicationStatus.APPROVED
                || application.getStatus() == ApplicationStatus.REJECTED) {
            throw new RuntimeException("该申请单已结案（" + applicationStatusText(application.getStatus())
                    + "），不能再标记加急");
        }
        if (application.isUrgent()) {
            throw new RuntimeException("该申请单已标记加急（加急人：" + application.getUrgentOperator()
                    + "，加急时间：" + formatTime(application.getUrgentTime())
                    + "），请勿重复加急；如需调整可先取消加急后重新标记");
        }
        application.setUrgent(true);
        application.setUrgentReason(request.getReason().trim());
        application.setUrgentOperator(request.getOperator().trim());
        application.setUrgentTime(LocalDateTime.now());
        application = applicationRepository.save(application);

        logRepository.save(new TransferApplicationLog(application.getId(), "URGENT",
                application.getUrgentOperator(),
                "标记加急，加急原因：" + application.getUrgentReason()));

        fillItemCounts(application);
        enrichHaltMarkers(List.of(application));
        return application;
    }

    /**
     * 取消加急：必须填写取消说明（记入操作记录）。未加急的申请单取消时给出明确提示。
     */
    @Transactional
    public TransferApplication cancelUrgent(Long id, UrgentRequest request) {
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new RuntimeException("取消加急必须填写取消说明");
        }
        TransferApplication application = applicationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("划转申请不存在"));
        if (!application.isUrgent()) {
            throw new RuntimeException("该申请单未标记加急，无需取消");
        }
        String operator = request.getOperator().trim();
        String reason = request.getReason().trim();
        application.setUrgent(false);
        application.setUrgentReason(null);
        application.setUrgentOperator(null);
        application.setUrgentTime(null);
        application = applicationRepository.save(application);

        logRepository.save(new TransferApplicationLog(application.getId(), "URGENT_CANCEL",
                operator, "取消加急，取消说明：" + reason));

        fillItemCounts(application);
        enrichHaltMarkers(List.of(application));
        return application;
    }

    /**
     * 批量审批通过：每条明细独立事务处理，单条失败（业务校验失败或抛异常）不影响其他明细，
     * 始终为每个入参返回一条逐条结果
     */
    public List<ItemProcessResult> approve(ApprovalRequest request) {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        List<ItemProcessResult> results = new ArrayList<>();
        for (Long itemId : request.getItemIds().stream().distinct().toList()) {
            results.add(processOneItem(txTemplate, itemId,
                    () -> doApprove(itemId, request.getApprover().trim())));
        }
        return results;
    }

    /**
     * 批量驳回：每条明细独立事务处理，驳回必须填写原因。
     * 单条失败不影响其他明细，始终为每个入参返回一条逐条结果
     */
    public List<ItemProcessResult> reject(ApprovalRequest request) {
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new RuntimeException("驳回时必须填写驳回原因");
        }
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        List<ItemProcessResult> results = new ArrayList<>();
        for (Long itemId : request.getItemIds().stream().distinct().toList()) {
            results.add(processOneItem(txTemplate, itemId,
                    () -> doReject(itemId, request.getApprover().trim(), request.getReason().trim())));
        }
        return results;
    }

    /**
     * 在独立事务中处理单条明细：
     * 业务校验不通过时返回失败结果（无数据写入）；抛出任何异常都会使该明细事务整体回滚
     * （归属、流水、日志均不落地），仅记录为该条失败结果，不中断批量循环
     */
    private ItemProcessResult processOneItem(TransactionTemplate txTemplate, Long itemId,
                                             Supplier<ItemProcessResult> action) {
        try {
            return txTemplate.execute(status -> action.get());
        } catch (DataAccessException e) {
            // 锁等待超时、死锁、约束冲突等：事务已回滚，该明细未生效
            log.error("审批明细 #" + itemId + " 发生数据访问异常，事务已回滚", e);
            return ItemProcessResult.fail(itemId, resolveSpringCode(itemId),
                    "处理失败（数据异常，该明细未生效），请刷新页面后重试");
        } catch (RuntimeException e) {
            log.warn("审批明细 #{} 处理失败，事务已回滚：{}", itemId, e.getMessage());
            return ItemProcessResult.fail(itemId, resolveSpringCode(itemId), e.getMessage());
        }
    }

    /** 事务回滚结束后查询弹簧编号，用于补全逐条失败结果 */
    private String resolveSpringCode(Long itemId) {
        try {
            return itemRepository.findSpringCodeById(itemId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 单条明细审批通过，由 approve() 在独立事务中调用。
     * 锁顺序固定为：申请单行 -> 明细行 -> 弹簧档案行，与其他路径保持一致，避免死锁。
     */
    private ItemProcessResult doApprove(Long itemId, String approver) {
        // 投影预读（不加载受管实体），仅用于不存在/重复操作的快速失败；加锁后必须重新读取最新状态
        TransferApplicationItemRepository.ItemPreview preview = itemRepository.findPreviewById(itemId).orElse(null);
        if (preview == null) {
            return ItemProcessResult.fail(itemId, null, "申请明细不存在");
        }
        if (preview.getStatus() != ItemStatus.PENDING) {
            return ItemProcessResult.fail(itemId, preview.getSpringCode(),
                    "该申请已由 " + preview.getApprover() + " 处理（" + statusText(preview.getStatus()) + "），请勿重复操作");
        }

        // 锁定申请单行：串行化同一申请单的并发审批，不同申请单互不阻塞
        TransferApplication application = applicationRepository.findByIdForUpdate(preview.getApplicationId())
                .orElseThrow(() -> new RuntimeException("划转申请不存在"));

        // 持有申请单锁后加锁读取明细，此时一级缓存中无该实体，得到的是并发场景下的最新状态
        TransferApplicationItem item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new RuntimeException("申请明细不存在"));
        if (item.getStatus() != ItemStatus.PENDING) {
            return ItemProcessResult.fail(itemId, item.getSpringCode(),
                    "该申请已由 " + item.getApprover() + " 处理（" + statusText(item.getStatus()) + "），请勿重复操作");
        }

        // 悲观锁锁定弹簧，串行化并发审批对同一弹簧的归属变更
        SpringArchive spring = springArchiveRepository.findByIdForUpdate(item.getSpringId()).orElse(null);
        if (spring == null) {
            return ItemProcessResult.fail(itemId, item.getSpringCode(), "弹簧档案不存在");
        }
        if (spring.getCurrentLineId().equals(item.getToLineId())) {
            return ItemProcessResult.fail(itemId, item.getSpringCode(),
                    "弹簧当前已归属目标产线「" + item.getToLineName() + "」，无需划转，请驳回该申请");
        }
        // 申请提交后弹簧被封存的，审批同样拦截，封存期间不允许归属变更
        if (spring.isSealed()) {
            return ItemProcessResult.fail(itemId, item.getSpringCode(),
                    "弹簧处于封存状态（" + spring.getSealSummary() + "），封存期间不能划转，请先解封");
        }
        // 申请提交后目标产线被登记停台的，审批拦截，停台期间该产线不能作为划转接收方
        ProductionLine toLine = productionLineRepository.findById(item.getToLineId()).orElse(null);
        if (toLine == null) {
            return ItemProcessResult.fail(itemId, item.getSpringCode(), "目标产线不存在");
        }
        if (toLine.isHalted()) {
            return ItemProcessResult.fail(itemId, item.getSpringCode(),
                    toLine.getHaltSummary() + "，停台期间不能接收划转，请待复台后再审批通过");
        }

        // 原子状态流转（双保险），防止并发审批重复生效
        int updated = itemRepository.approveIfPending(itemId, approver, LocalDateTime.now(),
                ItemStatus.APPROVED, ItemStatus.PENDING);
        if (updated == 0) {
            return ItemProcessResult.fail(itemId, item.getSpringCode(), "该申请已被其他审批人处理，请刷新查看最新状态");
        }

        ProductionLine fromLine = productionLineRepository.findById(spring.getCurrentLineId())
                .orElseThrow(() -> new RuntimeException("弹簧 " + spring.getSpringCode() + " 的当前产线不存在"));

        // 审批通过后才更新弹簧归属并生成划转流水；状态流转成功后才执行到这里，因此每条通过明细只会执行一次
        spring.setCurrentLineId(item.getToLineId());
        springArchiveRepository.save(spring);

        TransferRecord record = new TransferRecord();
        record.setSpringId(spring.getId());
        record.setSpringCode(spring.getSpringCode());
        record.setFromLineId(fromLine.getId());
        record.setFromLineName(fromLine.getLineName());
        record.setToLineId(toLine.getId());
        record.setToLineName(toLine.getLineName());
        record.setOperator(approver);
        record.setOperateTime(LocalDateTime.now());
        record.setRemark("划转申请单 " + application.getApplicationNo() + " 审批通过（申请人：" + application.getApplicant() + "）");
        record = transferRecordRepository.save(record);

        itemRepository.updateTransferRecordId(itemId, record.getId());

        logRepository.save(new TransferApplicationLog(item.getApplicationId(), "APPROVE", approver,
                "审批通过：" + item.getSpringCode() + "，" + fromLine.getLineName() + " → " + item.getToLineName()
                        + "，已生成划转流水 #" + record.getId()));

        refreshApplicationStatus(item.getApplicationId());

        // 划转可能导致划入方预警/超载、划出方恢复正常：按双方最新负载同步告警事件
        syncAlertsAfterTransfer(fromLine.getId(), item.getToLineId());

        return ItemProcessResult.success(itemId, item.getSpringCode(),
                "审批通过，弹簧已划转至「" + item.getToLineName() + "」");
    }

    /**
     * 划转落地后同步划出/划入产线的负载告警事件。
     * 在审批明细事务提交后由独立事务执行，事件同步失败不影响已生效的划转。
     */
    private void syncAlertsAfterTransfer(Long... lineIds) {
        try {
            for (Long lineId : lineIds) {
                lineLoadService.getLineStats(lineId)
                        .ifPresent(stats -> loadAlertService.syncEvents(List.of(stats)));
            }
        } catch (Exception e) {
            log.error("划转后告警事件同步失败，看板刷新时会补偿同步", e);
        }
    }

    /**
     * 单条明细驳回，由 reject() 在独立事务中调用。
     * 驳回不改变弹簧归属、不生成流水。锁顺序与通过路径保持一致。
     */
    private ItemProcessResult doReject(Long itemId, String approver, String reason) {
        TransferApplicationItemRepository.ItemPreview preview = itemRepository.findPreviewById(itemId).orElse(null);
        if (preview == null) {
            return ItemProcessResult.fail(itemId, null, "申请明细不存在");
        }
        if (preview.getStatus() != ItemStatus.PENDING) {
            return ItemProcessResult.fail(itemId, preview.getSpringCode(),
                    "该申请已由 " + preview.getApprover() + " 处理（" + statusText(preview.getStatus()) + "），请勿重复操作");
        }

        // 与通过路径使用同一把申请单行锁，保证通过/驳回并发时状态汇总仍然准确
        applicationRepository.findByIdForUpdate(preview.getApplicationId())
                .orElseThrow(() -> new RuntimeException("划转申请不存在"));

        TransferApplicationItem item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new RuntimeException("申请明细不存在"));
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
     * 根据明细处理进度汇总申请单状态。
     * 必须在持有申请单行锁的事务中调用：FOR UPDATE 当前读保证看到其他事务已提交的最新明细，
     * 从根本上避免并发处理后申请单状态与明细数量不一致（刷新页面后依然准确）
     */
    private void refreshApplicationStatus(Long applicationId) {
        long pending = itemRepository.countByApplicationIdAndStatusForUpdate(applicationId, ItemStatus.PENDING);
        long approved = itemRepository.countByApplicationIdAndStatusForUpdate(applicationId, ItemStatus.APPROVED);
        long rejected = itemRepository.countByApplicationIdAndStatusForUpdate(applicationId, ItemStatus.REJECTED);
        long total = pending + approved + rejected;

        ApplicationStatus status;
        if (total == 0) {
            status = ApplicationStatus.PENDING;
        } else if (pending == total) {
            status = ApplicationStatus.PENDING;
        } else if (approved == total) {
            status = ApplicationStatus.APPROVED;
        } else if (rejected == total) {
            status = ApplicationStatus.REJECTED;
        } else {
            status = ApplicationStatus.PARTIAL;
        }
        applicationRepository.updateStatus(applicationId, status);

        // 申请单结案（全部通过/全部驳回）后加急标记自动解除并留痕，
        // 保证加急单始终对应仍有待批行的单据，看板「加急待批」按剩余待批行计数，与审批台刷新后一致
        if (status == ApplicationStatus.APPROVED || status == ApplicationStatus.REJECTED) {
            int cleared = applicationRepository.clearUrgentIfMarked(applicationId);
            if (cleared > 0) {
                logRepository.save(new TransferApplicationLog(applicationId, "URGENT_CANCEL", "SYSTEM",
                        "申请单已结案（" + applicationStatusText(status) + "），加急标记自动解除"));
            }
        }
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

    private String applicationStatusText(ApplicationStatus status) {
        return switch (status) {
            case PENDING -> "待审批";
            case APPROVED -> "全部通过";
            case REJECTED -> "全部驳回";
            case PARTIAL -> "部分处理";
        };
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "-" : time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
