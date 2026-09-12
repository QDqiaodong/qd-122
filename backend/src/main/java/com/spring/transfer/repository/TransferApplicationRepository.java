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
import java.util.Collection;
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

    /** 看板统计：加急且仍待审批（待审批/部分处理）的申请单数 */
    long countByUrgentTrueAndStatusIn(Collection<ApplicationStatus> statuses);

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
