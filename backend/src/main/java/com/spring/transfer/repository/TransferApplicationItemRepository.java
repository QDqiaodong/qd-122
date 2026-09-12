package com.spring.transfer.repository;

import com.spring.transfer.common.ItemStatus;
import com.spring.transfer.entity.TransferApplicationItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransferApplicationItemRepository extends JpaRepository<TransferApplicationItem, Long> {
    /** 锁前预读投影：不加载受管实体，避免一级缓存导致加锁后拿到旧状态 */
    interface ItemPreview {
        Long getApplicationId();
        String getSpringCode();
        ItemStatus getStatus();
        String getApprover();
    }

    List<TransferApplicationItem> findByApplicationIdOrderByIdAsc(Long applicationId);

    List<TransferApplicationItem> findBySpringIdInAndStatus(Collection<Long> springIds, ItemStatus status);

    long countByApplicationIdAndStatus(Long applicationId, ItemStatus status);

    /** 流向指定产线的待审批明细数：登记停台时用于提示存在待审批单 */
    long countByToLineIdAndStatus(Long toLineId, ItemStatus status);

    /** 流向指定产线且仍含待审批明细的申请单数（去重）：登记停台时给出涉及单量 */
    @Query("SELECT COUNT(DISTINCT i.applicationId) FROM TransferApplicationItem i " +
           "WHERE i.toLineId = :toLineId AND i.status = :status")
    long countDistinctApplicationByToLineIdAndStatus(Long toLineId, ItemStatus status);

    /** 轻量预读（不加载实体），拿到所属申请单等信息后再加锁 */
    @Query("SELECT i.applicationId AS applicationId, i.springCode AS springCode, i.status AS status, i.approver AS approver " +
           "FROM TransferApplicationItem i WHERE i.id = :id")
    Optional<ItemPreview> findPreviewById(Long id);

    /**
     * 悲观写锁读取明细：在持有申请单行锁后使用，确保拿到并发审批后的最新状态
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM TransferApplicationItem i WHERE i.id = :id")
    Optional<TransferApplicationItem> findByIdForUpdate(Long id);

    /**
     * 加锁当前读统计某状态明细数：在持有申请单行锁后使用，
     * FOR UPDATE 强制读取其他事务已提交的最新数据，避免快照读导致的状态汇总错误
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COUNT(i) FROM TransferApplicationItem i WHERE i.applicationId = :applicationId AND i.status = :status")
    long countByApplicationIdAndStatusForUpdate(Long applicationId, ItemStatus status);

    /** 轻量查询弹簧编号，用于异常回滚后补全逐条结果 */
    @Query("SELECT i.springCode FROM TransferApplicationItem i WHERE i.id = :id")
    Optional<String> findSpringCodeById(Long id);

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
