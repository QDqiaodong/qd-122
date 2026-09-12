package com.spring.transfer.service;

import com.spring.transfer.common.SampleStatus;
import com.spring.transfer.dto.CloseSampleRequest;
import com.spring.transfer.dto.RegisterSampleRequest;
import com.spring.transfer.dto.UpdateMeasuredRequest;
import com.spring.transfer.entity.ElasticSample;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.entity.SpringArchive;
import com.spring.transfer.repository.ElasticSampleRepository;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.SpringArchiveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 弹力抽检留样：质量员按产线登记实测弹力系数，系统按该线适用区间判定偏离；
 * 偏离留样闭环后弹簧档案黄标不自动摘除，须质量主管在档案页点「摘标加签」（工号+加签说明）后才摘除；
 * 黄标件（待闭环或已闭环待加签）不能进入划转申请；
 * 留样单必须填写处置结论才能闭环，已闭环单实测系数不可再改。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticSampleService {
    private final ElasticSampleRepository sampleRepository;
    private final SpringArchiveRepository springArchiveRepository;
    private final ProductionLineRepository productionLineRepository;

    public Page<ElasticSample> findAll(SampleStatus status, Long lineId, Boolean deviated,
                                       String keyword, Pageable pageable) {
        Page<ElasticSample> page = sampleRepository.findByCondition(
                status, lineId, deviated,
                keyword == null || keyword.isBlank() ? null : keyword.trim(),
                pageable);
        enrichCurrentLineNames(page.getContent());
        return page;
    }

    public ElasticSample findById(Long id) {
        ElasticSample sample = sampleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("留样单不存在"));
        enrichCurrentLineNames(List.of(sample));
        return sample;
    }

    /**
     * 登记留样：按弹簧当前归属产线快照该线适用区间，实测系数偏离区间即标记偏离（挂黄标）。
     */
    @Transactional
    public ElasticSample register(RegisterSampleRequest request) {
        // 锁定弹簧档案行，与划转审批的归属变更串行化，保证快照的是登记瞬间的产线
        SpringArchive spring = springArchiveRepository.findByIdForUpdate(request.getSpringId())
                .orElseThrow(() -> new RuntimeException("弹簧档案不存在"));
        Long lineId = request.getLineId() == null ? spring.getCurrentLineId() : request.getLineId();
        if (!lineId.equals(spring.getCurrentLineId())) {
            throw new RuntimeException("弹簧 " + spring.getSpringCode()
                    + " 当前归属产线与登记产线不一致，质量员须按弹簧当前所在产线登记留样");
        }
        ProductionLine line = productionLineRepository.findById(lineId)
                .orElseThrow(() -> new RuntimeException("登记产线不存在"));

        BigDecimal measured = request.getMeasuredCoefficient();
        boolean deviated = isOutOfRange(measured, line.getElasticMin(), line.getElasticMax());

        ElasticSample sample = new ElasticSample();
        sample.setSampleNo(generateSampleNo());
        sample.setSpringId(spring.getId());
        sample.setSpringCode(spring.getSpringCode());
        sample.setModel(spring.getModel());
        sample.setLineId(line.getId());
        sample.setLineCode(line.getLineCode());
        sample.setLineName(line.getLineName());
        sample.setMeasuredCoefficient(measured);
        sample.setLineElasticMin(line.getElasticMin());
        sample.setLineElasticMax(line.getElasticMax());
        sample.setDeviated(deviated);
        sample.setStatus(SampleStatus.OPEN);
        sample.setOperator(request.getOperator().trim());
        ElasticSample saved = sampleRepository.save(sample);
        enrichCurrentLineNames(List.of(saved));
        return saved;
    }

    /**
     * 修改实测系数：仅待闭环单可改；按登记时的产线适用区间快照重算偏离标记
     * （黄标随之挂上/摘下，区间以登记产线为准，不受后续产线阈值调整影响）。
     */
    @Transactional
    public ElasticSample updateMeasured(Long id, UpdateMeasuredRequest request) {
        ElasticSample sample = sampleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("留样单不存在"));
        if (!sample.isOpen()) {
            throw new RuntimeException("留样单 " + sample.getSampleNo() + " 已闭环，已闭环单不能修改实测系数");
        }
        sample.setMeasuredCoefficient(request.getMeasuredCoefficient());
        sample.setDeviated(isOutOfRange(request.getMeasuredCoefficient(),
                sample.getLineElasticMin(), sample.getLineElasticMax()));
        ElasticSample saved = sampleRepository.save(sample);
        enrichCurrentLineNames(List.of(saved));
        return saved;
    }

    /**
     * 闭环：处置结论必填；闭环后黄标不摘除，须质量主管在档案页对该件摘标加签后才摘除。
     */
    @Transactional
    public ElasticSample close(Long id, CloseSampleRequest request) {
        ElasticSample sample = sampleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("留样单不存在"));
        if (!sample.isOpen()) {
            throw new RuntimeException("留样单 " + sample.getSampleNo() + " 已闭环，请勿重复闭环；"
                    + "闭环人：" + sample.getCloseOperator() + "，闭环时间：" + sample.getCloseTime());
        }
        sample.setStatus(SampleStatus.CLOSED);
        sample.setConclusion(request.getConclusion().trim());
        sample.setCloseOperator(request.getOperator().trim());
        sample.setCloseTime(LocalDateTime.now());
        ElasticSample saved = sampleRepository.save(sample);
        enrichCurrentLineNames(List.of(saved));
        return saved;
    }

    /**
     * 为弹簧列表批量挂接黄标标记。黄标分两阶段摘除：
     * 1. 偏离留样全部闭环后仍保持黄标（已闭环待加签）；
     * 2. 质量主管在档案页完成摘标加签（工号+说明，以加签时间为水位）后才摘除；
     * 加签后若再出现新的偏离留样（待闭环、或加签后闭环的），黄标重新挂上。
     */
    @Transactional(readOnly = true)
    public void markYellowFlags(List<SpringArchive> springs) {
        if (springs.isEmpty()) {
            return;
        }
        List<Long> springIds = springs.stream().map(SpringArchive::getId).toList();
        List<ElasticSample> deviations =
                sampleRepository.findBySpringIdInAndDeviatedTrueOrderByIdAsc(springIds);
        Map<Long, List<ElasticSample>> bySpring = deviations.stream()
                .collect(Collectors.groupingBy(ElasticSample::getSpringId));
        springs.forEach(s -> {
            List<ElasticSample> samples = bySpring.getOrDefault(s.getId(), List.of());
            int pending = 0;
            int unacknowledged = 0;
            LocalDateTime countersignTime = s.getFlagCountersignTime();
            for (ElasticSample sample : samples) {
                if (!isAcknowledged(sample, countersignTime)) {
                    unacknowledged++;
                    if (sample.isOpen()) {
                        pending++;
                    }
                }
            }
            s.setOpenDeviationCount(unacknowledged);
            s.setPendingDeviationCount(pending);
            s.setYellowFlag(unacknowledged > 0);
        });
    }

    /**
     * 划转门禁：黄标件不能勾进划转申请，包含两种情形：
     * - 仍有偏离留样待闭环；
     * - 偏离留样已闭环但质量主管尚未摘标加签。
     * 提示中给出留样编号、实测系数与登记产线适用区间。
     */
    @Transactional(readOnly = true)
    public void assertNoOpenDeviation(List<SpringArchive> springs) {
        if (springs.isEmpty()) {
            return;
        }
        List<Long> springIds = springs.stream().map(SpringArchive::getId).toList();
        List<ElasticSample> deviations =
                sampleRepository.findBySpringIdInAndDeviatedTrueOrderByIdAsc(springIds);
        Map<Long, String> codeMap = springs.stream()
                .collect(Collectors.toMap(SpringArchive::getId, SpringArchive::getSpringCode));
        Map<Long, LocalDateTime> countersignMap = springs.stream()
                .collect(Collectors.toMap(SpringArchive::getId,
                        s -> s.getFlagCountersignTime() == null ? LocalDateTime.MIN : s.getFlagCountersignTime()));
        Map<Long, List<ElasticSample>> bySpring = deviations.stream()
                .filter(sm -> !isAcknowledged(sm, countersignMap.get(sm.getSpringId())))
                .collect(Collectors.groupingBy(ElasticSample::getSpringId, HashMap::new, Collectors.toList()));
        if (bySpring.isEmpty()) {
            return;
        }
        String details = bySpring.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    List<ElasticSample> list = e.getValue();
                    ElasticSample first = list.get(0);
                    first.setSpringCode(codeMap.getOrDefault(e.getKey(), first.getSpringCode()));
                    boolean allClosed = list.stream().noneMatch(ElasticSample::isOpen);
                    String tail;
                    if (allClosed) {
                        tail = "（偏离留样已闭环 " + list.size() + " 张，待质量主管摘标加签）";
                    } else if (list.size() > 1) {
                        tail = " 等 " + list.size() + " 张偏离留样未闭环";
                    } else {
                        tail = "（留样 " + first.getSampleNo() + "：实测 " + first.getMeasuredCoefficient()
                              + "，" + first.getLineName() + "适用区间 " + first.rangeText() + "）";
                    }
                    return first.getSpringCode() + tail;
                })
                .collect(Collectors.joining("；"));
        throw new RuntimeException("以下弹簧为黄标件，闭环处置并经质量主管摘标加签前不能进入划转申请: " + details);
    }

    /** 偏离留样是否已被质量主管加签确认：加签时间晚于该留样闭环时间（待闭环单恒为未确认） */
    private boolean isAcknowledged(ElasticSample sample, LocalDateTime countersignTime) {
        if (sample.isOpen() || sample.getCloseTime() == null || countersignTime == null) {
            return false;
        }
        return countersignTime.isAfter(sample.getCloseTime());
    }

    /** 实测系数是否偏离适用区间；区间端点为 null 表示该侧不限 */
    private boolean isOutOfRange(BigDecimal measured, BigDecimal min, BigDecimal max) {
        if (min != null && measured.compareTo(min) < 0) {
            return true;
        }
        return max != null && measured.compareTo(max) > 0;
    }

    /** 挂接弹簧当前所在产线名称（留样上的产线为登记时快照） */
    private void enrichCurrentLineNames(List<ElasticSample> samples) {
        if (samples.isEmpty()) {
            return;
        }
        Set<Long> springIds = samples.stream().map(ElasticSample::getSpringId).collect(Collectors.toSet());
        List<SpringArchive> springs = springArchiveRepository.findAllById(springIds);
        Set<Long> lineIds = springs.stream().map(SpringArchive::getCurrentLineId).collect(Collectors.toSet());
        Map<Long, String> lineNameMap = productionLineRepository.findAllById(lineIds).stream()
                .collect(Collectors.toMap(ProductionLine::getId, ProductionLine::getLineName));
        Map<Long, String> nameMap = springs.stream()
                .collect(Collectors.toMap(SpringArchive::getId,
                        s -> lineNameMap.getOrDefault(s.getCurrentLineId(), null)));
        samples.forEach(s -> s.setSpringCurrentLineName(nameMap.get(s.getSpringId())));
    }

    private String generateSampleNo() {
        String no;
        do {
            no = "ES" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        } while (sampleRepository.existsBySampleNo(no));
        return no;
    }
}
