package com.spring.transfer.repository;

import com.spring.transfer.common.ItemStatus;
import com.spring.transfer.entity.TransferApplicationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface TransferApplicationItemRepository extends JpaRepository<TransferApplicationItem, Long> {
    List<TransferApplicationItem> findByApplicationIdOrderByIdAsc(Long applicationId);

    List<TransferApplicationItem> findBySpringIdInAndStatus(Collection<Long> springIds, ItemStatus status);

    long countByApplicationIdAndStatus(Long applicationId, ItemStatus status);

    /**
     * 仅当明细仍处于待审批时才更新为已通过，返回 0 表示已被并发处理
     */
    @Modifying
    @Query("UPDATE TransferApplicationItem i SET i.status = :approvedStatus, i.approver = :approver, i.approveTime = :approveTime " +
           "WHERE i.id = :id AND i.status = :pendingStatus")
    int approveIfPending(Long id, String approver, LocalDateTime approveTime,
                         ItemStatus approvedStatus, ItemStatus pendingStatus);

    /**
     * 仅当明细仍处于待审批时才更新为已驳回，返回 0 表示已被并发处理
     */
    @Modifying
    @Query("UPDATE TransferApplicationItem i SET i.status = :rejectedStatus, i.approver = :approver, i.approveTime = :approveTime, i.rejectReason = :rejectReason " +
           "WHERE i.id = :id AND i.status = :pendingStatus")
    int rejectIfPending(Long id, String approver, LocalDateTime approveTime, String rejectReason,
                        ItemStatus rejectedStatus, ItemStatus pendingStatus);

    @Modifying
    @Query("UPDATE TransferApplicationItem i SET i.transferRecordId = :transferRecordId WHERE i.id = :id")
    int updateTransferRecordId(Long id, Long transferRecordId);
}
