package com.spring.transfer.repository;

import com.spring.transfer.common.LineHaltStatus;
import com.spring.transfer.entity.ProductionLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductionLineRepository extends JpaRepository<ProductionLine, Long> {
    Optional<ProductionLine> findByLineCode(String lineCode);
    boolean existsByLineCode(String lineCode);

    /** 悲观写锁读取产线，串行化停台/复台与并发划转审批对产线停台标记的读取 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM ProductionLine l WHERE l.id = :id")
    Optional<ProductionLine> findByIdForUpdate(Long id);

    /** 审批台按「目标产线是否停台」筛选时，一次性取出对应状态的产线ID */
    List<ProductionLine> findByHaltStatus(LineHaltStatus haltStatus);
}
