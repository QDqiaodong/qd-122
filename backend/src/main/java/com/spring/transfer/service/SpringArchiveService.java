package com.spring.transfer.service;

import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.entity.SpringArchive;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.SpringArchiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpringArchiveService {
    private final SpringArchiveRepository springArchiveRepository;
    private final ProductionLineRepository productionLineRepository;
    private final ElasticSpecCacheService elasticSpecCacheService;

    public Page<SpringArchive> findAll(Long lineId, String keyword, Pageable pageable) {
        Page<SpringArchive> page = springArchiveRepository.findByCondition(lineId, keyword, pageable);
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
