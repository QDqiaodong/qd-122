package com.spring.transfer.repository;

import com.spring.transfer.entity.TransferApplicationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransferApplicationLogRepository extends JpaRepository<TransferApplicationLog, Long> {
    List<TransferApplicationLog> findByApplicationIdOrderByOperateTimeAscIdAsc(Long applicationId);
}
