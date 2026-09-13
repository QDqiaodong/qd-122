package com.spring.transfer.init;

import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.repository.ProductionLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 为历史产线补齐工装剩余刀次与刀次门槛默认值。
 * 仅在字段为空时写入默认值，已人工维护过（含换刀复位）的配置不覆盖。
 */
@Slf4j
@Component
@Order(21)
@RequiredArgsConstructor
public class ToolingCutsInitializer implements CommandLineRunner {
    /** 默认工装剩余刀次（新刀寿命），高于门槛，默认不到门槛 */
    private static final int DEFAULT_REMAINING_CUTS = 500;
    /** 默认刀次门槛：剩余刀次低于该值即到门槛 */
    private static final int DEFAULT_THRESHOLD = 100;

    private final ProductionLineRepository productionLineRepository;

    @Override
    @Transactional
    public void run(String... args) {
        int updated = 0;
        for (ProductionLine line : productionLineRepository.findAll()) {
            boolean changed = false;
            if (line.getToolingRemainingCuts() == null) {
                line.setToolingRemainingCuts(DEFAULT_REMAINING_CUTS);
                changed = true;
            }
            if (line.getToolingCutThreshold() == null) {
                line.setToolingCutThreshold(DEFAULT_THRESHOLD);
                changed = true;
            }
            if (changed) {
                productionLineRepository.save(line);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("已为 {} 条产线补齐默认工装剩余刀次/门槛配置", updated);
        }
    }
}
