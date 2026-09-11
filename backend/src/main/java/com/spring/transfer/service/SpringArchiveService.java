package com.spring.transfer.service;

import com.spring.transfer.common.SealStatus;
import com.spring.transfer.dto.SealRequest;
import com.spring.transfer.dto.UnsealRequest;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.entity.SpringArchive;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.SpringArchiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpringArchiveService {
    private final SpringArchiveRepository springArchiveRepository;
    private final ProductionLineRepository productionLineRepository;
    private final ElasticSpecCacheService elasticSpecCacheService;

    public Page<SpringArchive> findAll(Long lineId, SealStatus sealStatus, String keyword, Pageable pageable) {
        Page<SpringArchive> page = springArchiveRepository.findByCondition(lineId, sealStatus, keyword, pageable);
        enrichWithLineNames(page.getContent());
        return page;
    }

    public Map<Long, List<SpringArchive>> groupByLine() {
        List<SpringArchive> all = springArchiveRepository.findAll();
        enrichWithLineNames(all);
        return all.stream()
                .collect(Collectors.groupingBy(
                        SpringArchive::getCurrentLineId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    public Optional<SpringArchive> findById(Long id) {
        Optional<SpringArchive> opt = springArchiveRepository.findById(id);
        opt.ifPresent(this::enrichWithLineName);
        return opt;
    }

    public Optional<SpringArchive> findBySpringCode(String springCode) {
        Optional<SpringArchive> opt = springArchiveRepository.findBySpringCode(springCode);
        opt.ifPresent(this::enrichWithLineName);
        return opt;
    }

    @Transactional
    public SpringArchive save(SpringArchive springArchive) {
        if (springArchiveRepository.existsBySpringCode(springArchive.getSpringCode())) {
            throw new RuntimeException("弹簧编号已存在");
        }
        if (springArchive.getCurrentLineId() == null) {
            throw new RuntimeException("请选择归属产线");
        }
        springArchive.setInitialLineId(springArchive.getCurrentLineId());
        // 新建档案一律为正常状态，封存必须走质量员登记流程
        springArchive.setSealStatus(SealStatus.NONE);
        SpringArchive saved = springArchiveRepository.save(springArchive);
        elasticSpecCacheService.addElasticSpec(saved.getElasticCoefficient());
        enrichWithLineName(saved);
        return saved;
    }

    @Transactional
    public SpringArchive updateCurrentLine(Long springId, Long newLineId) {
        SpringArchive spring = springArchiveRepository.findById(springId)
                .orElseThrow(() -> new RuntimeException("弹簧档案不存在"));
        spring.setCurrentLineId(newLineId);
        return springArchiveRepository.save(spring);
    }

    /**
     * 封存弹簧：质量员对抽检不合格/待复测弹簧登记封存原因与预计解封日。
     * 封存期间该弹簧不能进入划转申请与调拨模拟（在提交/预估/审批环节拦截）。
     */
    @Transactional
    public SpringArchive seal(Long springId, SealRequest request) {
        SpringArchive spring = springArchiveRepository.findByIdForUpdate(springId)
                .orElseThrow(() -> new RuntimeException("弹簧档案不存在"));
        if (spring.isSealed()) {
            throw new RuntimeException("弹簧 " + spring.getSpringCode() + " 已处于封存状态，请勿重复封存；"
                    + "当前封存信息：" + spring.getSealSummary());
        }
        if (request.getExpectedUnsealDate().isBefore(LocalDate.now())) {
            throw new RuntimeException("预计解封日不能早于今天");
        }
        spring.setSealStatus(SealStatus.SEALED);
        spring.setSealReason(request.getReason().trim());
        spring.setSealExpectedUnsealDate(request.getExpectedUnsealDate());
        spring.setSealOperator(request.getOperator().trim());
        spring.setSealTime(LocalDateTime.now());
        // 新一轮封存清空上一轮解封信息，避免展示过期结论
        spring.setUnsealOperator(null);
        spring.setUnsealTime(null);
        spring.setUnsealConclusion(null);
        SpringArchive saved = springArchiveRepository.save(spring);
        enrichWithLineName(saved);
        return saved;
    }

    /**
     * 解封弹簧：必须填写解封结论（复测结果/处置结论），解封后恢复可划转、可模拟。
     * 封存信息保留在档案上作为最近一次封存记录，便于追溯。
     */
    @Transactional
    public SpringArchive unseal(Long springId, UnsealRequest request) {
        SpringArchive spring = springArchiveRepository.findByIdForUpdate(springId)
                .orElseThrow(() -> new RuntimeException("弹簧档案不存在"));
        if (!spring.isSealed()) {
            throw new RuntimeException("弹簧 " + spring.getSpringCode() + " 未处于封存状态，无需解封");
        }
        spring.setSealStatus(SealStatus.NONE);
        spring.setUnsealOperator(request.getOperator().trim());
        spring.setUnsealTime(LocalDateTime.now());
        spring.setUnsealConclusion(request.getConclusion().trim());
        SpringArchive saved = springArchiveRepository.save(spring);
        enrichWithLineName(saved);
        return saved;
    }

    private void enrichWithLineNames(List<SpringArchive> springs) {
        List<Long> lineIds = springs.stream()
                .map(SpringArchive::getCurrentLineId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> lineNameMap = productionLineRepository.findAllById(lineIds).stream()
                .collect(Collectors.toMap(ProductionLine::getId, ProductionLine::getLineName));
        springs.forEach(s -> {
            s.setCurrentLineName(lineNameMap.get(s.getCurrentLineId()));
            if (s.getInitialLineId() != null) {
                s.setInitialLineName(lineNameMap.get(s.getInitialLineId()));
            }
        });
    }

    private void enrichWithLineName(SpringArchive spring) {
        enrichWithLineNames(Collections.singletonList(spring));
    }
}
