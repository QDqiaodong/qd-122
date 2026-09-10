package com.spring.transfer.repository;

import com.spring.transfer.common.ApplicationStatus;
import com.spring.transfer.entity.TransferApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TransferApplicationRepository extends JpaRepository<TransferApplication, Long> {
    boolean existsByApplicationNo(String applicationNo);

    @Query("SELECT a FROM TransferApplication a WHERE " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:keyword IS NULL OR a.applicationNo LIKE %:keyword% OR a.applicant LIKE %:keyword%)")
    Page<TransferApplication> findByCondition(ApplicationStatus status, String keyword, Pageable pageable);

    /**
     * 悲观写锁锁定申请单：串行化同一申请单内各明细的并发审批，
     * 保证状态汇总在锁内基于最新数据计算；不同申请单之间互不阻塞
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM TransferApplication a WHERE a.id = :id")
    Optional<TransferApplication> findByIdForUpdate(Long id);

    @Modifying
    @Query("UPDATE TransferApplication a SET a.status = :status WHERE a.id = :id")
    int updateStatus(Long id, ApplicationStatus status);
}
