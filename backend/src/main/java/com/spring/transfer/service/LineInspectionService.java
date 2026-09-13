package com.spring.transfer.service;

import com.spring.transfer.dto.LineInspectionRequest;
import com.spring.transfer.entity.LineInspection;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.repository.LineInspectionRepository;
import com.spring.transfer.repository.ProductionLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 产线开班点检服务。
 *
 * 质量员开班前为产线登记气源压力、工装完好情况与点检人（三项必填，缺一不能提交）；
 * 系统按标准气源压力区间（0.40~0.80 MPa）与工装完好情况判定点检是否通过。
 * 当日未点检或点检未通过的产线不能作为调拨模拟的接收方；
 * 负载预警看板展示该线最近一次点检时间与是否通过。
 */
@Service
@RequiredArgsConstructor
public class LineInspectionService {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final LineInspectionRepository inspectionRepository;
    private final ProductionLineRepository productionLineRepository;

    /**
     * 登记开班点检：三项（气源压力、工装完好、点检人）由 Bean Validation 强校验，缺一不能提交。
     * 点检结论由系统判定：工装完好且气源压力在标准区间内为通过。
     */
    @Transactional
    public LineInspection register(LineInspectionRequest request) {
        ProductionLine line = productionLineRepository.findById(request.getLineId())
                .orElseThrow(() -> new RuntimeException("点检产线不存在，ID: " + request.getLineId()));

        LineInspection inspection = new LineInspection();
        inspection.setInspectionNo(generateInspectionNo());
        inspection.setLineId(line.getId());
        inspection.setLineCode(line.getLineCode());
        inspection.setLineName(line.getLineName());
        inspection.setAirPressure(request.getAirPressure());
        inspection.setToolingIntact(request.getToolingIntact());
        inspection.setInspector(request.getInspector().trim());
        inspection.setPassed(isPassing(request.getAirPressure(), request.getToolingIntact()));
        inspection.setInspectTime(LocalDateTime.now());
        return inspectionRepository.save(inspection);
    }

    /** 该产线的点检历史（点检时间倒序） */
    public List<LineInspection> findByLine(Long lineId) {
        return inspectionRepository.findByLineIdOrderByInspectTimeDescIdDesc(lineId);
    }

    /** 该产线最近一次点检 */
    public Optional<LineInspection> latestOfLine(Long lineId) {
        return inspectionRepository.findTopByLineIdOrderByInspectTimeDescIdDesc(lineId);
    }

    /** 全部产线的最近一次点检，key 为产线ID（看板/列表一次性挂接，避免逐线查询） */
    public Map<Long, LineInspection> latestMap() {
        return inspectionRepository.findAll().stream()
                .collect(Collectors.toMap(LineInspection::getLineId, Function.identity(),
                        (a, b) -> a.getInspectTime().isAfter(b.getInspectTime())
                                || (a.getInspectTime().isEqual(b.getInspectTime()) && a.getId() > b.getId()) ? a : b));
    }

    /**
     * 调拨模拟接收方守卫：当日未开班点检或点检未通过的产线不能作为接收方。
     * 返回 null 表示可接收，否则返回明确的拦截原因。
     */
    public String receiveBlockReason(ProductionLine line) {
        Optional<LineInspection> latestOpt = latestOfLine(line.getId());
        if (latestOpt.isEmpty() || !latestOpt.get().isToday()) {
            return "产线「" + line.getLineName() + "」未开班点检，不能进行调拨模拟；"
                    + "请质量员先完成开班点检登记（气源压力、工装完好、点检人）";
        }
        LineInspection latest = latestOpt.get();
        if (!Boolean.TRUE.equals(latest.getPassed())) {
            return "产线「" + line.getLineName() + "」开班点检未通过（" + latest.getResultSummary()
                    + "，点检人：" + latest.getInspector()
                    + "，点检时间：" + latest.getInspectTime().format(TIME_FORMATTER)
                    + "），不能作为划转接收方，请整改后重新点检";
        }
        return null;
    }

    /** 点检通过判定：工装完好且气源压力在标准区间内 */
    private boolean isPassing(BigDecimal airPressure, Boolean toolingIntact) {
        return Boolean.TRUE.equals(toolingIntact)
                && airPressure != null
                && airPressure.compareTo(LineInspection.AIR_PRESSURE_MIN) >= 0
                && airPressure.compareTo(LineInspection.AIR_PRESSURE_MAX) <= 0;
    }

    private String generateInspectionNo() {
        String no;
        do {
            no = "INSP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        } while (inspectionRepository.existsByInspectionNo(no));
        return no;
    }
}
