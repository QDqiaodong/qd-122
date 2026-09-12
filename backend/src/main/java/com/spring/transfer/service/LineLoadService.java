package com.spring.transfer.service;

import com.spring.transfer.dto.LineLoadBoardResponse;
import com.spring.transfer.dto.LineLoadDetailResponse;
import com.spring.transfer.dto.LineLoadStats;
import com.spring.transfer.dto.LineSimulationEstimate;
import com.spring.transfer.dto.LineThresholdUpdateRequest;
import com.spring.transfer.dto.LoadAlertEventResponse;
import com.spring.transfer.dto.LoadStatus;
import com.spring.transfer.dto.SimulationEstimateResponse;
import com.spring.transfer.common.ApplicationStatus;
import com.spring.transfer.entity.LoadAlertEvent;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.entity.SpringArchive;
import com.spring.transfer.entity.TransferRecord;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.SpringArchiveRepository;
import com.spring.transfer.repository.TransferApplicationRepository;
import com.spring.transfer.repository.TransferRecordRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 产线负载预警看板服务。
 *
 * 负载状态由三个维度共同决定：
 * 1. 数量维度：当前归属弹簧数量 vs 日承载阈值（&gt;阈值=超载，=阈值或≥80%=预警）；
 * 2. 弹力系数维度：当前归属弹簧中存在超出该产线适用系数区间的，触发预警；
 * 3. 划转趋势维度：近 7 天持续净流入且负载率已≥80%，短期内存在超载风险，触发预警。
 *
 * 看板汇总数字与分组列表基于同一次计算结果组装，保证统计与列表一致。
 */
@Service
public class LineLoadService {
    /** 划转趋势统计窗口（天） */
    public static final int TREND_DAYS = 7;
    /** 预警负载率阈值（百分比） */
    private static final BigDecimal WARN_RATE = new BigDecimal("80");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProductionLineRepository productionLineRepository;
    private final SpringArchiveRepository springArchiveRepository;
    private final TransferRecordRepository transferRecordRepository;
    private final TransferApplicationRepository applicationRepository;
    /** 告警事件随负载计算结果同步：异常建事件、恢复自动关闭；@Lazy 规避循环依赖 */
    private final LoadAlertService loadAlertService;

    public LineLoadService(ProductionLineRepository productionLineRepository,
                           SpringArchiveRepository springArchiveRepository,
                           TransferRecordRepository transferRecordRepository,
                           TransferApplicationRepository applicationRepository,
                           @Lazy LoadAlertService loadAlertService) {
        this.productionLineRepository = productionLineRepository;
        this.springArchiveRepository = springArchiveRepository;
        this.transferRecordRepository = transferRecordRepository;
        this.applicationRepository = applicationRepository;
        this.loadAlertService = loadAlertService;
    }

    public LineLoadBoardResponse getBoard() {
        List<LineLoadStats> all = buildStats();

        // 先按最新计算结果同步告警事件（异常新建/恢复自动关闭），再读取未关闭事件挂到概览项，
        // 保证顶部统计、状态分组与告警标记来自同一次计算
        loadAlertService.syncEvents(all);
        Map<String, Integer> alertCounts = loadAlertService.attachOpenEvents(all);

        Map<LoadStatus, List<LineLoadStats>> grouped = all.stream()
                .collect(Collectors.groupingBy(
                        s -> LoadStatus.valueOf(s.getStatus()),
                        Collectors.toList()));

        LineLoadBoardResponse response = new LineLoadBoardResponse();
        response.setTotalLines(all.size());
        response.setTotalSprings(all.stream().mapToInt(LineLoadStats::getSpringCount).sum());
        response.setNormalCount(grouped.getOrDefault(LoadStatus.NORMAL, List.of()).size());
        response.setWarningCount(grouped.getOrDefault(LoadStatus.WARNING, List.of()).size());
        response.setOverloadCount(grouped.getOrDefault(LoadStatus.OVERLOAD, List.of()).size());
        response.setPendingAlertCount(alertCounts.get("pending"));
        response.setOpenAlertCount(alertCounts.get("open"));
        // 加急待批数与审批台同源：加急标记持久化在申请单上，结案自动解除，刷新后统计与列表一致
        response.setUrgentPendingCount((int) applicationRepository.countByUrgentTrueAndStatusIn(
                List.of(ApplicationStatus.PENDING, ApplicationStatus.PARTIAL)));
        response.setLines(all);
        response.setNormalLines(grouped.getOrDefault(LoadStatus.NORMAL, List.of()));
        response.setWarningLines(grouped.getOrDefault(LoadStatus.WARNING, List.of()));
        response.setOverloadLines(grouped.getOrDefault(LoadStatus.OVERLOAD, List.of()));
        return response;
    }

    public Optional<LineLoadDetailResponse> getLineDetail(Long lineId) {
        Optional<ProductionLine> lineOpt = productionLineRepository.findById(lineId);
        if (lineOpt.isEmpty()) {
            return Optional.empty();
        }
        ProductionLine line = lineOpt.get();
        LocalDateTime after = LocalDateTime.now().minusDays(TREND_DAYS);

        Map<Long, List<SpringArchive>> springsByLine = springArchiveRepository.findAll().stream()
                .collect(Collectors.groupingBy(SpringArchive::getCurrentLineId));
        List<SpringArchive> springs = springsByLine.getOrDefault(lineId, new ArrayList<>());
        enrichLineNames(springs);

        Map<Long, Long> countMap = toCountMap(springsByLine);
        TrendAggregation trend = aggregateTrend(transferRecordRepository.findByOperateTimeAfter(after));

        LineLoadStats stats = buildStats(line,
                countMap.getOrDefault(lineId, 0L).intValue(),
                countOutOfRange(line, springsByLine.getOrDefault(lineId, List.of())),
                trend.inCount.getOrDefault(lineId, 0),
                trend.outCount.getOrDefault(lineId, 0));

        // 明细同样先同步事件，保证打开抽屉看到的是最新处置闭环状态
        loadAlertService.syncEvents(List.of(stats));
        loadAlertService.attachOpenEvents(List.of(stats));

        LineLoadDetailResponse detail = new LineLoadDetailResponse();
        detail.setStats(stats);
        detail.setSprings(springs);
        detail.setRecentTransfers(transferRecordRepository.findRecentByLineId(lineId, after));

        Map<Long, String> currentStatusMap = Map.of(lineId, stats.getStatus());
        loadAlertService.findOpenEvent(lineId)
                .ifPresent(event -> detail.setOpenAlertEvent(
                        loadAlertService.toResponse(event, stats.getStatus())));
        List<LoadAlertEvent> events = loadAlertService.findLineEvents(lineId, null);
        detail.setAlertEvents(loadAlertService.toResponses(events, currentStatusMap));
        return Optional.of(detail);
    }

    /** 计算单条产线当前实时负载状态，供告警处置等场景轻量查询 */
    public String getCurrentStatus(Long lineId) {
        return getLineStats(lineId)
                .map(LineLoadStats::getStatus)
                .orElse(LoadStatus.NORMAL.name());
    }

    /** 计算单条产线当前实时负载概览（产线不存在时返回 empty） */
    public Optional<LineLoadStats> getLineStats(Long lineId) {
        Optional<ProductionLine> lineOpt = productionLineRepository.findById(lineId);
        if (lineOpt.isEmpty()) {
            return Optional.empty();
        }
        ProductionLine line = lineOpt.get();
        LocalDateTime after = LocalDateTime.now().minusDays(TREND_DAYS);
        Map<Long, List<SpringArchive>> springsByLine = springArchiveRepository.findAll().stream()
                .collect(Collectors.groupingBy(SpringArchive::getCurrentLineId));
        TrendAggregation trend = aggregateTrend(transferRecordRepository.findByOperateTimeAfter(after));
        List<SpringArchive> springs = springsByLine.getOrDefault(lineId, List.of());
        return Optional.of(buildStats(line, springs.size(), countOutOfRange(line, springs),
                trend.inCount.getOrDefault(lineId, 0),
                trend.outCount.getOrDefault(lineId, 0)));
    }

    /**
     * 工序调拨模拟：预估所选弹簧划至拟接收产线后，各受影响产线（接收产线 + 各划出产线）
     * 的承载率、弹力系数越界数量与预警原因。
     *
     * 数量与系数维度按模拟后的归属重新计算；划转趋势维度沿用近 7 天实际流水
     * （模拟不改变历史数据）。计算口径与负载预警看板完全一致，保证模拟结果可信。
     */
    public SimulationEstimateResponse simulateTransfer(List<Long> springIds, Long toLineId) {
        ProductionLine toLine = productionLineRepository.findById(toLineId)
                .orElseThrow(() -> new RuntimeException("拟接收产线不存在"));
        // 停台校验：临时停台期间该产线不能作为划转接收方，提示中给出停台原因与预计复台时间
        if (toLine.isHalted()) {
            throw new RuntimeException(toLine.getHaltSummary()
                    + "，停台期间不能作为划转接收方，请待复台后再模拟或保存方案");
        }

        List<Long> distinctIds = springIds.stream().distinct().toList();
        List<SpringArchive> allSprings = springArchiveRepository.findAll();
        Map<Long, SpringArchive> springMap = allSprings.stream()
                .collect(Collectors.toMap(SpringArchive::getId, Function.identity()));
        List<Long> missing = distinctIds.stream().filter(id -> !springMap.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new RuntimeException("以下弹簧档案不存在，ID: " + missing);
        }
        List<SpringArchive> selected = distinctIds.stream().map(springMap::get).toList();

        // 同产线校验：已归属拟接收产线的弹簧无需划转
        List<String> sameLineCodes = selected.stream()
                .filter(s -> s.getCurrentLineId().equals(toLineId))
                .map(SpringArchive::getSpringCode)
                .toList();
        if (!sameLineCodes.isEmpty()) {
            throw new RuntimeException("以下弹簧已归属拟接收产线「" + toLine.getLineName() + "」，无需划转: "
                    + String.join(", ", sameLineCodes));
        }

        // 封存校验：封存中的弹簧（抽检不合格/待复测）不能进入调拨模拟，提示中给出封存原因与预计解封日
        List<SpringArchive> sealedSprings = selected.stream().filter(SpringArchive::isSealed).toList();
        if (!sealedSprings.isEmpty()) {
            String details = sealedSprings.stream().map(SpringArchive::getSealSummary)
                    .collect(Collectors.joining("；"));
            throw new RuntimeException("以下弹簧处于封存状态，封存期间不能进入调拨模拟，请先解封: " + details);
        }

        Map<Long, List<SpringArchive>> springsByLine = allSprings.stream()
                .collect(Collectors.groupingBy(SpringArchive::getCurrentLineId));
        Map<Long, List<SpringArchive>> movedOutByLine = selected.stream()
                .collect(Collectors.groupingBy(SpringArchive::getCurrentLineId));

        LocalDateTime after = LocalDateTime.now().minusDays(TREND_DAYS);
        TrendAggregation trend = aggregateTrend(transferRecordRepository.findByOperateTimeAfter(after));

        Map<Long, ProductionLine> lineMap = productionLineRepository.findAll().stream()
                .collect(Collectors.toMap(ProductionLine::getId, Function.identity()));

        // 受影响产线：拟接收产线排最前，其余划出产线按ID升序
        Set<Long> affectedLineIds = new LinkedHashSet<>();
        affectedLineIds.add(toLineId);
        movedOutByLine.keySet().stream().sorted().forEach(affectedLineIds::add);

        List<LineSimulationEstimate> lines = new ArrayList<>();
        for (Long lineId : affectedLineIds) {
            ProductionLine line = lineMap.get(lineId);
            if (line == null) {
                throw new RuntimeException("产线不存在，ID: " + lineId);
            }
            boolean isTarget = lineId.equals(toLineId);
            List<SpringArchive> current = springsByLine.getOrDefault(lineId, List.of());
            List<SpringArchive> movedOut = movedOutByLine.getOrDefault(lineId, List.of());

            // 模拟后归属：划出产线移除划出弹簧，接收产线追加全部选中弹簧
            Set<Long> movedOutIds = movedOut.stream().map(SpringArchive::getId).collect(Collectors.toSet());
            List<SpringArchive> simulated = current.stream()
                    .filter(s -> !movedOutIds.contains(s.getId()))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (isTarget) {
                simulated.addAll(selected);
            }

            int recentIn = trend.inCount.getOrDefault(lineId, 0);
            int recentOut = trend.outCount.getOrDefault(lineId, 0);
            LineLoadStats currentStats = buildStats(line, current.size(),
                    countOutOfRange(line, current), recentIn, recentOut);
            LineLoadStats simulatedStats = buildStats(line, simulated.size(),
                    countOutOfRange(line, simulated), recentIn, recentOut);

            LineSimulationEstimate estimate = new LineSimulationEstimate();
            estimate.setLineId(line.getId());
            estimate.setLineCode(line.getLineCode());
            estimate.setLineName(line.getLineName());
            estimate.setDirection(isTarget ? "IN" : "OUT");
            estimate.setMoveInCount(isTarget ? selected.size() : 0);
            estimate.setMoveOutCount(movedOut.size());
            estimate.setCurrentCount(currentStats.getSpringCount());
            estimate.setSimulatedCount(simulatedStats.getSpringCount());
            estimate.setDailyCapacityThreshold(line.getDailyCapacityThreshold());
            estimate.setCurrentLoadRate(currentStats.getLoadRate());
            estimate.setSimulatedLoadRate(simulatedStats.getLoadRate());
            estimate.setCurrentOutOfRangeCount(currentStats.getOutOfRangeCount());
            estimate.setSimulatedOutOfRangeCount(simulatedStats.getOutOfRangeCount());
            estimate.setCurrentStatus(currentStats.getStatus());
            estimate.setSimulatedStatus(simulatedStats.getStatus());
            estimate.setReasons(simulatedStats.getReasons());
            lines.add(estimate);
        }

        SimulationEstimateResponse response = new SimulationEstimateResponse();
        response.setToLineId(toLine.getId());
        response.setToLineName(toLine.getLineName());
        response.setSpringCount(selected.size());
        response.setLines(lines);
        return response;
    }

    @Transactional
    public ProductionLine updateThreshold(Long lineId, LineThresholdUpdateRequest request) {
        ProductionLine line = productionLineRepository.findById(lineId)
                .orElseThrow(() -> new RuntimeException("产线不存在"));
        if (request.getDailyCapacityThreshold() == null) {
            throw new RuntimeException("日承载阈值不能为空");
        }
        if (request.getElasticMin() != null && request.getElasticMax() != null
                && request.getElasticMin().compareTo(request.getElasticMax()) > 0) {
            throw new RuntimeException("弹力系数下限不能大于上限");
        }
        line.setDailyCapacityThreshold(request.getDailyCapacityThreshold());
        line.setElasticMin(request.getElasticMin());
        line.setElasticMax(request.getElasticMax());
        ProductionLine saved = productionLineRepository.save(line);

        // 阈值调整可能立即触发或解除告警：按新口径重算并同步事件
        getLineStats(saved.getId()).ifPresent(stats -> loadAlertService.syncEvents(List.of(stats)));
        return saved;
    }

    // ---------------------------------------------------------------------
    // 负载计算
    // ---------------------------------------------------------------------

    private List<LineLoadStats> buildStats() {
        LocalDateTime after = LocalDateTime.now().minusDays(TREND_DAYS);

        Map<Long, List<SpringArchive>> springsByLine = springArchiveRepository.findAll().stream()
                .collect(Collectors.groupingBy(SpringArchive::getCurrentLineId));
        Map<Long, Long> countMap = toCountMap(springsByLine);
        TrendAggregation trend = aggregateTrend(transferRecordRepository.findByOperateTimeAfter(after));

        List<LineLoadStats> statsList = productionLineRepository.findAll().stream()
                .map(line -> {
                    int count = countMap.getOrDefault(line.getId(), 0L).intValue();
                    int outOfRange = countOutOfRange(line, springsByLine.getOrDefault(line.getId(), List.of()));
                    return buildStats(line, count, outOfRange,
                            trend.inCount.getOrDefault(line.getId(), 0),
                            trend.outCount.getOrDefault(line.getId(), 0));
                })
                .collect(Collectors.toList());

        // 超载 > 预警 > 正常；同状态内按负载率、当前数量降序
        return statsList.stream()
                .sorted(Comparator
                        .comparingInt((LineLoadStats s) -> statusRank(s.getStatus()))
                        .thenComparing(LineLoadStats::getLoadRate,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(LineLoadStats::getSpringCount, Comparator.reverseOrder())
                        .thenComparing(LineLoadStats::getLineId))
                .collect(Collectors.toList());
    }

    private LineLoadStats buildStats(ProductionLine line, int count, int outOfRange,
                                     int recentIn, int recentOut) {
        LineLoadStats stats = new LineLoadStats();
        stats.setLineId(line.getId());
        stats.setLineCode(line.getLineCode());
        stats.setLineName(line.getLineName());
        stats.setDescription(line.getDescription());
        stats.setDailyCapacityThreshold(line.getDailyCapacityThreshold());
        stats.setElasticMin(line.getElasticMin());
        stats.setElasticMax(line.getElasticMax());
        // 停台标记随负载概览一并下发：看板列表可按是否停台筛选并展示停台原因/预计复台时间
        stats.setHalted(line.isHalted());
        stats.setHaltReason(line.getHaltReason());
        stats.setHaltExpectedResumeTime(line.getHaltExpectedResumeTime());
        stats.setHaltOperator(line.getHaltOperator());
        stats.setHaltTime(line.getHaltTime());
        stats.setResumeOperator(line.getResumeOperator());
        stats.setResumeTime(line.getResumeTime());
        stats.setResumeConclusion(line.getResumeConclusion());
        stats.setSpringCount(count);
        stats.setOutOfRangeCount(outOfRange);
        stats.setTrendDays(TREND_DAYS);
        stats.setRecentInCount(recentIn);
        stats.setRecentOutCount(recentOut);
        stats.setRecentNetIn(recentIn - recentOut);

        BigDecimal rate = null;
        if (line.getDailyCapacityThreshold() != null && line.getDailyCapacityThreshold() > 0) {
            rate = BigDecimal.valueOf(count)
                    .multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(line.getDailyCapacityThreshold()), 2, RoundingMode.HALF_UP);
        }
        stats.setLoadRate(rate);

        List<String> reasons = new ArrayList<>();
        boolean overload = false;
        Integer threshold = line.getDailyCapacityThreshold();

        // 维度一：数量 vs 日承载阈值
        if (threshold == null) {
            reasons.add("尚未配置日承载阈值，无法评估数量负载，请先维护阈值");
        } else if (count > threshold) {
            overload = true;
            reasons.add(String.format(
                    "当前归属弹簧 %d 件，超过日承载阈值 %d 件（负载率 %s%%），产线已超载",
                    count, threshold, rateStr(rate)));
        } else if (count == threshold) {
            reasons.add(String.format(
                    "当前归属弹簧 %d 件，已达到日承载阈值 %d 件（负载率 100%%），无余量接收划转",
                    count, threshold));
        } else if (rate != null && rate.compareTo(WARN_RATE) >= 0) {
            reasons.add(String.format(
                    "当前归属弹簧 %d 件，接近日承载阈值 %d 件（负载率 %s%%）",
                    count, threshold, rateStr(rate)));
        }

        // 维度二：弹力系数适用区间
        if (outOfRange > 0) {
            reasons.add(String.format(
                    "有 %d 件弹簧的弹力系数不在该产线适用区间 %s 内，可能不适合本产线加工",
                    outOfRange, formatElasticRange(line.getElasticMin(), line.getElasticMax())));
        }

        // 维度三：近 7 天划转趋势
        int netIn = recentIn - recentOut;
        if (netIn > 0 && rate != null && rate.compareTo(WARN_RATE) >= 0) {
            reasons.add(String.format(
                    "近 %d 天净流入 %d 件（划入 %d / 划出 %d），且负载率已达 %s%%，持续流入将导致超载",
                    TREND_DAYS, netIn, recentIn, recentOut, rateStr(rate)));
        }

        stats.setReasons(reasons);
        if (overload) {
            stats.setStatus(LoadStatus.OVERLOAD.name());
        } else if (!reasons.isEmpty()) {
            stats.setStatus(LoadStatus.WARNING.name());
        } else {
            stats.setStatus(LoadStatus.NORMAL.name());
        }
        return stats;
    }

    private int countOutOfRange(ProductionLine line, List<SpringArchive> springs) {
        BigDecimal min = line.getElasticMin();
        BigDecimal max = line.getElasticMax();
        if (min == null && max == null) {
            return 0;
        }
        return (int) springs.stream()
                .filter(s -> {
                    BigDecimal k = s.getElasticCoefficient();
                    if (k == null) {
                        return false;
                    }
                    boolean below = min != null && k.compareTo(min) < 0;
                    boolean above = max != null && k.compareTo(max) > 0;
                    return below || above;
                })
                .count();
    }

    private int statusRank(String status) {
        if (LoadStatus.OVERLOAD.name().equals(status)) {
            return 0;
        }
        if (LoadStatus.WARNING.name().equals(status)) {
            return 1;
        }
        return 2;
    }

    private String rateStr(BigDecimal rate) {
        return rate == null ? "-" : rate.stripTrailingZeros().toPlainString();
    }

    private String formatElasticRange(BigDecimal min, BigDecimal max) {
        String low = min == null ? "0" : min.stripTrailingZeros().toPlainString();
        String high = max == null ? "∞" : max.stripTrailingZeros().toPlainString();
        return low + " ~ " + high + " N/mm";
    }

    private Map<Long, Long> toCountMap(Map<Long, List<SpringArchive>> springsByLine) {
        Map<Long, Long> map = new HashMap<>();
        springsByLine.forEach((lineId, list) -> map.put(lineId, (long) list.size()));
        return map;
    }

    private TrendAggregation aggregateTrend(List<TransferRecord> records) {
        TrendAggregation agg = new TrendAggregation();
        for (TransferRecord r : records) {
            agg.inCount.merge(r.getToLineId(), 1, Integer::sum);
            agg.outCount.merge(r.getFromLineId(), 1, Integer::sum);
        }
        return agg;
    }

    private void enrichLineNames(List<SpringArchive> springs) {
        if (springs.isEmpty()) {
            return;
        }
        List<Long> lineIds = springs.stream()
                .flatMap(s -> java.util.stream.Stream.of(s.getCurrentLineId(), s.getInitialLineId()))
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> nameMap = productionLineRepository.findAllById(lineIds).stream()
                .collect(Collectors.toMap(ProductionLine::getId, ProductionLine::getLineName));
        springs.forEach(s -> {
            s.setCurrentLineName(nameMap.get(s.getCurrentLineId()));
            s.setInitialLineName(nameMap.get(s.getInitialLineId()));
        });
    }

    private static class TrendAggregation {
        final Map<Long, Integer> inCount = new HashMap<>();
        final Map<Long, Integer> outCount = new HashMap<>();
    }
}
