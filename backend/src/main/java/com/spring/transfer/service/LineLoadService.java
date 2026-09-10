package com.spring.transfer.service;

import com.spring.transfer.dto.LineLoadBoardResponse;
import com.spring.transfer.dto.LineLoadDetailResponse;
import com.spring.transfer.dto.LineLoadStats;
import com.spring.transfer.dto.LineThresholdUpdateRequest;
import com.spring.transfer.dto.LoadStatus;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.entity.SpringArchive;
import com.spring.transfer.entity.TransferRecord;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.SpringArchiveRepository;
import com.spring.transfer.repository.TransferRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
@RequiredArgsConstructor
public class LineLoadService {
    /** 划转趋势统计窗口（天） */
    public static final int TREND_DAYS = 7;
    /** 预警负载率阈值（百分比） */
    private static final BigDecimal WARN_RATE = new BigDecimal("80");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProductionLineRepository productionLineRepository;
    private final SpringArchiveRepository springArchiveRepository;
    private final TransferRecordRepository transferRecordRepository;

    public LineLoadBoardResponse getBoard() {
        List<LineLoadStats> all = buildStats();

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

        LineLoadDetailResponse detail = new LineLoadDetailResponse();
        detail.setStats(stats);
        detail.setSprings(springs);
        detail.setRecentTransfers(transferRecordRepository.findRecentByLineId(lineId, after));
        return Optional.of(detail);
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
        return productionLineRepository.save(line);
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
