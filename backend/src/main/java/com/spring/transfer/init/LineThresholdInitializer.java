package com.spring.transfer.init;

import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.repository.ProductionLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 为历史产线补齐默认日承载阈值与弹力系数适用区间。
 * 仅在字段为空时写入默认值，已人工维护过的配置不覆盖。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class LineThresholdInitializer implements CommandLineRunner {
    private static final int DEFAULT_THRESHOLD = 5;
    private static final BigDecimal DEFAULT_ELASTIC_MIN = new BigDecimal("0.2000");
    private static final BigDecimal DEFAULT_ELASTIC_MAX = new BigDecimal("6.0000");

    private final ProductionLineRepository productionLineRepository;

    @Override
    @Transactional
    public void run(String... args) {
        int updated = 0;
        for (ProductionLine line : productionLineRepository.findAll()) {
            boolean changed = false;
            if (line.getDailyCapacityThreshold() == null) {
                line.setDailyCapacityThreshold(DEFAULT_THRESHOLD);
                changed = true;
            }
            if (line.getElasticMin() == null) {
                line.setElasticMin(DEFAULT_ELASTIC_MIN);
                changed = true;
            }
            if (line.getElasticMax() == null) {
                line.setElasticMax(DEFAULT_ELASTIC_MAX);
                changed = true;
            }
            if (changed) {
                productionLineRepository.save(line);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("已为 {} 条产线补齐默认负载阈值配置", updated);
        }
    }
}
