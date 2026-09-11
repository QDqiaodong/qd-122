package com.spring.transfer.repository;

import com.spring.transfer.common.SimulationStatus;
import com.spring.transfer.entity.TransferSimulation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TransferSimulationRepository extends JpaRepository<TransferSimulation, Long> {
    boolean existsBySimulationNo(String simulationNo);

    @Query("SELECT s FROM TransferSimulation s WHERE " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:keyword IS NULL OR s.simulationNo LIKE %:keyword% OR s.operator LIKE %:keyword%)")
    Page<TransferSimulation> findByCondition(SimulationStatus status, String keyword, Pageable pageable);

    /**
     * 悲观写锁锁定方案：串行化同一方案的采用/作废操作，防止并发采用生成重复划转申请
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TransferSimulation s WHERE s.id = :id")
    Optional<TransferSimulation> findByIdForUpdate(Long id);
}
