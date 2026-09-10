package com.spring.transfer.repository;

import com.spring.transfer.common.ApplicationStatus;
import com.spring.transfer.entity.TransferApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferApplicationRepository extends JpaRepository<TransferApplication, Long> {
    boolean existsByApplicationNo(String applicationNo);

    @Query("SELECT a FROM TransferApplication a WHERE " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:keyword IS NULL OR a.applicationNo LIKE %:keyword% OR a.applicant LIKE %:keyword%)")
    Page<TransferApplication> findByCondition(ApplicationStatus status, String keyword, Pageable pageable);

    @Modifying
    @Query("UPDATE TransferApplication a SET a.status = :status WHERE a.id = :id")
    int updateStatus(Long id, ApplicationStatus status);
}
