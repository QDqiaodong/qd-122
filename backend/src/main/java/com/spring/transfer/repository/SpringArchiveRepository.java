package com.spring.transfer.repository;

import com.spring.transfer.entity.SpringArchive;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpringArchiveRepository extends JpaRepository<SpringArchive, Long> {
    Optional<SpringArchive> findBySpringCode(String springCode);
    boolean existsBySpringCode(String springCode);
    List<SpringArchive> findByCurrentLineId(Long currentLineId);
    Page<SpringArchive> findByCurrentLineId(Long currentLineId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SpringArchive s WHERE s.id = :id")
    Optional<SpringArchive> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SpringArchive s WHERE s.id IN :ids ORDER BY s.id")
    List<SpringArchive> findAllByIdInForUpdate(Collection<Long> ids);
    
    @Query("SELECT DISTINCT s.elasticCoefficient FROM SpringArchive s ORDER BY s.elasticCoefficient")
    List<BigDecimal> findDistinctElasticCoefficients();
    
    @Query("SELECT s FROM SpringArchive s WHERE " +
           "(:lineId IS NULL OR s.currentLineId = :lineId) AND " +
           "(:keyword IS NULL OR s.springCode LIKE %:keyword% OR s.model LIKE %:keyword%)")
    Page<SpringArchive> findByCondition(Long lineId, String keyword, Pageable pageable);
}
