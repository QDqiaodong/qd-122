package com.spring.transfer.repository;

import com.spring.transfer.entity.TransferRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransferRecordRepository extends JpaRepository<TransferRecord, Long> {
    List<TransferRecord> findBySpringIdOrderByOperateTimeDesc(Long springId);

    @Query("SELECT t FROM TransferRecord t WHERE " +
           "(:springId IS NULL OR t.springId = :springId) AND " +
           "(:lineId IS NULL OR t.fromLineId = :lineId OR t.toLineId = :lineId)")
    Page<TransferRecord> findByCondition(Long springId, Long lineId, Pageable pageable);

    long countByToLineIdAndOperateTimeAfter(Long toLineId, LocalDateTime after);

    long countByFromLineIdAndOperateTimeAfter(Long fromLineId, LocalDateTime after);

    /**
     * 指定时间之后的全部划转流水（用于看板按产线聚合近 N 天划转趋势）。
     */
    @Query("SELECT t FROM TransferRecord t WHERE t.operateTime >= :after ORDER BY t.operateTime DESC")
    List<TransferRecord> findByOperateTimeAfter(LocalDateTime after);

    /**
     * 近 N 天与某产线相关的划转流水（划入或划出），按操作时间倒序。
     */
    @Query("SELECT t FROM TransferRecord t WHERE " +
           "(t.fromLineId = :lineId OR t.toLineId = :lineId) AND t.operateTime >= :after " +
           "ORDER BY t.operateTime DESC")
    List<TransferRecord> findRecentByLineId(Long lineId, LocalDateTime after);
}
