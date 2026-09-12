package com.spring.transfer.repository;

import com.spring.transfer.common.ApplicationStatus;
import com.spring.transfer.common.ItemStatus;
import com.spring.transfer.entity.TransferApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransferApplicationRepository extends JpaRepository<TransferApplication, Long> {
    boolean existsByApplicationNo(String applicationNo);

    /**
     * 按申请状态、关键词、「目标产线是否停台」与「是否加急」组合分页查询。
     * haltFilter：0-不按停台筛选 1-仅目标产线停台 2-仅目标产线未停台；
     * haltFilter != 0 时调用方保证 haltedLineIds 非空（无停台产线时以不可能命中的占位ID兜底）。
     * urgentFilter：0-不加急筛选 1-仅加急 2-仅未加急
     */
    @Query("SELECT a FROM TransferApplication a WHERE " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:keyword IS NULL OR a.applicationNo LIKE %:keyword% OR a.applicant LIKE %:keyword%) AND " +
           "(:haltFilter = 0 OR " +
           "  (:haltFilter = 1 AND a.toLineId IN :haltedLineIds) OR " +
           "  (:haltFilter = 2 AND a.toLineId NOT IN :haltedLineIds)) AND " +
           "(:urgentFilter = 0 OR " +
           "  (:urgentFilter = 1 AND a.urgent = true) OR " +
           "  (:urgentFilter = 2 AND a.urgent = false))")
    Page<TransferApplication> findByCondition(ApplicationStatus status, String keyword,
                                              int haltFilter, Collection<Long> haltedLineIds,
                                              int urgentFilter,
                                              Pageable pageable);

    /**
     * 看板加急待批统计行：每张仍带加急标记的申请单一行，含表头状态与其剩余待审批明细数。
     * 看板与审批台统一按「剩余待批行数」计数：只有加急申请单下仍处于待审批的明细才占用加急名额，
     * 已处理（通过/驳回）的明细不再计入。
     */
    interface UrgentPendingStat {
        Long getApplicationId();
        String getApplicationNo();
        ApplicationStatus getStatus();
        Long getPendingItemCount();
    }

    /**
     * 看板统计：逐张返回仍带加急标记申请单的表头状态与剩余待审批明细数（子查询按行计数）。
     * 以明细行（而非整单）为统计口径，部分处理单已处理行不再占用加急名额。
     */
    @Query("SELECT a.id AS applicationId, a.applicationNo AS applicationNo, a.status AS status, " +
           "(SELECT COUNT(i) FROM TransferApplicationItem i WHERE i.applicationId = a.id AND i.status = :pendingStatus) AS pendingItemCount " +
           "FROM TransferApplication a WHERE a.urgent = true")
    List<UrgentPendingStat> findUrgentPendingStats(@Param("pendingStatus") ItemStatus pendingStatus);

    /**
     * 申请单结案（全部通过/全部驳回）时自动解除加急标记，
     * 返回更新行数（>0 表示本次结案前该单处于加急状态，需补写自动解除日志）
     */
    @Modifying
    @Query("UPDATE TransferApplication a SET a.urgent = false, a.urgentReason = null, " +
           "a.urgentOperator = null, a.urgentTime = null WHERE a.id = :id AND a.urgent = true")
    int clearUrgentIfMarked(Long id);

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
