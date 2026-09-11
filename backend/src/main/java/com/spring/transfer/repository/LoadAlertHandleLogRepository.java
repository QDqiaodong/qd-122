package com.spring.transfer.repository;

import com.spring.transfer.entity.LoadAlertHandleLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoadAlertHandleLogRepository extends JpaRepository<LoadAlertHandleLog, Long> {
    List<LoadAlertHandleLog> findByEventIdOrderByOperateTimeAscIdAsc(Long eventId);

    List<LoadAlertHandleLog> findByEventIdInOrderByOperateTimeAscIdAsc(List<Long> eventIds);
}
