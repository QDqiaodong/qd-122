package com.spring.transfer.repository;

import com.spring.transfer.entity.TransferRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransferRecordRepository extends JpaRepository<TransferRecord, Long> {
    List<TransferRecord> findBySpringIdOrderByOperateTimeDesc(Long springId);
    
    @Query("SELECT t FROM TransferRecord t WHERE " +
           "(:springId IS NULL OR t.springId = :springId) AND " +
           "(:lineId IS NULL OR t.fromLineId = :lineId OR t.toLineId = :lineId)")
    Page<TransferRecord> findByCondition(Long springId, Long lineId, Pageable pageable);
}
