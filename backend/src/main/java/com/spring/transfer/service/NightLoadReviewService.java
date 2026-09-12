package com.spring.transfer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.transfer.common.ItemStatus;
import com.spring.transfer.common.ReviewStatus;
import com.spring.transfer.dto.ConfirmReviewRequest;
import com.spring.transfer.dto.CreateReviewRequest;
import com.spring.transfer.dto.LineLoadStats;
import com.spring.transfer.dto.NightLoadReviewResponse;
import com.spring.transfer.dto.PendingTransferItem;
import com.spring.transfer.dto.ReviewGuardResponse;
import com.spring.transfer.entity.NightLoadReview;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.entity.TransferApplication;
import com.spring.transfer.entity.TransferApplicationItem;
import com.spring.transfer.repository.NightLoadReviewRepository;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.TransferApplicationItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 夜班承载复核单服务。
 *
 * 交班调度员下班前按产线签发复核单，签发时固化四项承载快照：
 * 当前归属弹簧数、是否压到日承载、系数越界条数、次日必须跟进的待批划转。
 * 快照数字一经签发不再随后续归属变化而变动；接班调度员确认（必填跟进说明）后整单锁定。
 */
@Slf4j
@Service
public class NightLoadReviewService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NightLoadReviewRepository reviewRepository;
    private final ProductionLineRepository productionLineRepository;
    private final TransferApplicationItemRepository itemRepository;
    private final LineLoadService lineLoadService;
    private final ObjectMapper objectMapper;

    public NightLoadReviewService(NightLoadReviewRepository reviewRepository,
                                  ProductionLineRepository productionLineRepository,
                                  TransferApplicationItemRepository itemRepository,
                                  LineLoadService lineLoadService,
                                  ObjectMapper objectMapper) {
        this.reviewRepository = reviewRepository;
        this.productionLineRepository = productionLineRepository;
        this.itemRepository = itemRepository;
        this.lineLoadService = lineLoadService;
        this.objectMapper = objectMapper;
    }

    /** 按状态分页查询复核单（状态为 null 时查全部），并挂接待批划转快照 */
    public Page<NightLoadReviewResponse> findAll(ReviewStatus status, Pageable pageable) {
        Page<NightLoadReview> page = status == null
                ? reviewRepository.findAll(pageable)
                : reviewRepository.findByStatusOrderByIssueTimeDesc(status, pageable);
        return page.map(this::toResponse);
    }

    public NightLoadReviewResponse getDetail(Long id) {
        NightLoadReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("复核单不存在"));
        return toResponse(review);
    }

    /**
     * 签发夜班承载复核单：复用负载看板同一口径（LineLoadService.getLineStats）计算数量/阈值/越界，
     * 待批划转按流向该产线的待审批申请明细快照。同一产线同一夜班日期只能签发一张。
     */
    @Transactional
    public NightLoadReviewResponse issue(CreateReviewRequest request) {
        ProductionLine line = productionLineRepository.findById(request.getLineId())
                .orElseThrow(() -> new RuntimeException("复核产线不存在"));

        LocalDate reviewDate = LocalDate.now(ZONE);
        if (reviewRepository.existsByLineIdAndReviewDate(line.getId(), reviewDate)) {
            throw new RuntimeException("产线「" + line.getLineName() + "」今夜已签发过夜班承载复核单，"
                    + "请直接在已有复核单上交接，请勿重复签发");
        }

        // 与负载预警看板完全一致的口径：数量、阈值、承载率、系数越界条数
        LineLoadStats stats = lineLoadService.getLineStats(line.getId())
                .orElseThrow(() -> new RuntimeException("产线负载数据不存在"));

        int count = stats.getSpringCount();
        Integer threshold = stats.getDailyCapacityThreshold();
        BigDecimal loadRate = stats.getLoadRate();
        boolean capacityReached = threshold != null && count >= threshold;
        boolean overCapacity = threshold != null && count > threshold;

        // 次日必须跟进的待批划转：流向该产线的待审批明细（关联申请单快照）
        List<Object[]> rows = itemRepository.findPendingDetailWithApplication(line.getId(), ItemStatus.PENDING);
        List<PendingTransferItem> pendingItems = new ArrayList<>();
        for (Object[] row : rows) {
            TransferApplicationItem item = (TransferApplicationItem) row[0];
            TransferApplication app = (TransferApplication) row[1];
            pendingItems.add(new PendingTransferItem(
                    app.getId(), app.getApplicationNo(), app.getApplicant(),
                    item.getSpringCode(), item.getModel(), item.getElasticCoefficient(),
                    item.getFromLineName(), app.getReason(),
                    app.getApplyTime() == null ? null : app.getApplyTime().format(TIME_FORMATTER),
                    app.isUrgent()));
        }
        long pendingApplicationCount = pendingItems.stream()
                .map(PendingTransferItem::getApplicationId).distinct().count();

        NightLoadReview review = new NightLoadReview();
        review.setReviewNo(generateReviewNo());
        review.setReviewDate(reviewDate);
        review.setLineId(line.getId());
        review.setLineCode(line.getLineCode());
        review.setLineName(line.getLineName());
        review.setSpringCount(count);
        review.setDailyCapacityThreshold(threshold);
        review.setLoadRate(loadRate);
        review.setCapacityReached(capacityReached);
        review.setOverCapacity(overCapacity);
        review.setOutOfRangeCount(stats.getOutOfRangeCount());
        review.setPendingTransferCount(pendingItems.size());
        review.setPendingApplicationCount((int) pendingApplicationCount);
        review.setPendingTransferSnapshot(writeSnapshot(pendingItems));
        String remark = request.getHandoverRemark();
        review.setHandoverRemark(remark == null || remark.isBlank() ? null : remark.trim());
        review.setOperator(request.getOperator().trim());
        review.setIssueTime(LocalDateTime.now(ZONE));
        review.setStatus(ReviewStatus.PENDING);

        review = reviewRepository.save(review);
        return toResponse(review);
    }

    /**
     * 接班确认：必须填写跟进说明。
     * 悲观锁 + 条件更新双保险：仅待确认单可确认，已确认单不能重复确认、不能再改数字。
     */
    @Transactional
    public NightLoadReviewResponse confirm(Long id, ConfirmReviewRequest request) {
        String note = request.getFollowUpNote() == null ? "" : request.getFollowUpNote().trim();
        if (note.isEmpty()) {
            // DTO 校验之外再兜底，保证「不写跟进说明不能确认」
            throw new RuntimeException("不写跟进说明不能确认");
        }
        NightLoadReview review = reviewRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("复核单不存在"));
        if (review.getStatus() == ReviewStatus.CONFIRMED) {
            throw new RuntimeException("该复核单已由 " + review.getConfirmer() + " 于 "
                    + formatTime(review.getConfirmTime()) + " 确认，已确认的单不能重复确认或修改数字");
        }

        int updated = reviewRepository.confirmIfPending(id, ReviewStatus.CONFIRMED, note,
                request.getConfirmer().trim(), LocalDateTime.now(ZONE), ReviewStatus.PENDING);
        if (updated == 0) {
            throw new RuntimeException("该复核单已被其他接班人确认，请刷新查看最新状态");
        }
        NightLoadReview latest = reviewRepository.findById(id).orElseThrow();
        return toResponse(latest);
    }

    /**
     * 接班门禁：存在任意待确认复核单即阻塞新划转提交。
     * 接班人须先在看板确认昨夜复核单，全部确认后才放行。
     */
    public ReviewGuardResponse getGuard() {
        List<NightLoadReview> pending = reviewRepository.findByStatusOrderByIssueTimeDesc(ReviewStatus.PENDING);
        ReviewGuardResponse response = new ReviewGuardResponse();
        response.setAllowed(pending.isEmpty());
        response.setPendingCount(pending.size());
        response.setPendingReviews(pending.stream().map(this::toResponse).collect(Collectors.toList()));
        return response;
    }

    /** 划转提交前调用：有待确认复核单时抛出明确拦截原因 */
    public void assertNoPendingReviewForTransfer() {
        List<NightLoadReview> pending = reviewRepository.findByStatusOrderByIssueTimeDesc(ReviewStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        String detail = pending.stream()
                .map(r -> "「" + r.getLineName() + "」" + r.getReviewNo()
                        + "（签发人：" + r.getOperator() + "，签发时间：" + formatTime(r.getIssueTime()) + "）")
                .collect(Collectors.joining("、"));
        throw new RuntimeException("您还有 " + pending.size()
                + " 张夜班承载复核单未确认：" + detail
                + "。接班人须先确认昨夜复核单后，才能提交新的划转申请");
    }

    private NightLoadReviewResponse toResponse(NightLoadReview review) {
        NightLoadReviewResponse response = new NightLoadReviewResponse();
        response.setReview(review);
        response.setPendingTransfers(readSnapshot(review.getPendingTransferSnapshot()));
        return response;
    }

    private String writeSnapshot(List<PendingTransferItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            log.error("待批划转快照序列化失败", e);
            throw new RuntimeException("复核单待批划转快照生成失败");
        }
    }

    private List<PendingTransferItem> readSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<PendingTransferItem>>() {});
        } catch (JsonProcessingException e) {
            log.error("待批划转快照反序列化失败: {}", json, e);
            return List.of();
        }
    }

    private String generateReviewNo() {
        String no;
        do {
            no = "NR" + LocalDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        } while (reviewRepository.existsByReviewNo(no));
        return no;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "-" : time.format(TIME_FORMATTER);
    }
}
