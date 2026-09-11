package com.spring.transfer.repository;

import com.spring.transfer.entity.TransferSimulationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransferSimulationItemRepository extends JpaRepository<TransferSimulationItem, Long> {
    List<TransferSimulationItem> findBySimulationIdOrderByIdAsc(Long simulationId);

    int countBySimulationId(Long simulationId);
}
