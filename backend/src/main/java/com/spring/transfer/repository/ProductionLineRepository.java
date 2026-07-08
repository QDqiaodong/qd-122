package com.spring.transfer.repository;

import com.spring.transfer.entity.ProductionLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ProductionLineRepository extends JpaRepository<ProductionLine, Long> {
    Optional<ProductionLine> findByLineCode(String lineCode);
    boolean existsByLineCode(String lineCode);
}
