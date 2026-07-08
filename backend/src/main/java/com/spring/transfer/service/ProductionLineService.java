package com.spring.transfer.service;

import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.repository.ProductionLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductionLineService {
    private final ProductionLineRepository productionLineRepository;

    public List<ProductionLine> findAll() {
        return productionLineRepository.findAll();
    }

    public Optional<ProductionLine> findById(Long id) {
        return productionLineRepository.findById(id);
    }

    public Optional<ProductionLine> findByLineCode(String lineCode) {
        return productionLineRepository.findByLineCode(lineCode);
    }

    @Transactional
    public ProductionLine save(ProductionLine productionLine) {
        if (productionLineRepository.existsByLineCode(productionLine.getLineCode())) {
            throw new RuntimeException("产线编码已存在");
        }
        return productionLineRepository.save(productionLine);
    }
}
