package com.spring.transfer.repository;

import com.spring.transfer.common.ReviewStatus;
import com.spring.transfer.entity.NightLoadReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface NightLoadReviewRepository extends JpaRepository<NightLoadReview, Long> {
    boolean existsByReviewNo(String reviewNo);

    /** 同一产线同一夜班日期至多一张复核单（签发幂等/防重） */
    boolean existsByLineIdAndReviewDate(Long lineId, LocalDate reviewDate);

    /** 分页按状态筛选；status 为 null 时由 Service 走全量查询。结果按签发时间倒序 */
    Page<NightLoadReview> findByStatusOrderByIssueTimeDesc(ReviewStatus status, Pageable pageable);

    /** 看板/划转提交前检查：是否仍存在待确认复核单 */
    boolean existsByStatus(ReviewStatus status);

    /** 全部待确认复核单（按签发时间倒序） */
    List<NightLoadReview> findByStatusOrderByIssueTimeDesc(ReviewStatus status);

    /**
     * 悲观写锁读取复核单：串行化同一单的确认并发，
     * 保证「确认后数字锁定」在锁内基于最新状态判断，避免重复确认与确认期间改数。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM NightLoadReview r WHERE r.id = :id")
    Optional<NightLoadReview> findByIdForUpdate(Long id);

    /** 仅当仍为待确认时才置为已确认（写入跟进说明与确认人），返回 0 表示已被并发确认 */
    @Modifying
    @Query("UPDATE NightLoadReview r SET r.status = :confirmedStatus, r.followUpNote = :followUpNote, " +
           "r.confirmer = :confirmer, r.confirmTime = :confirmTime " +
           "WHERE r.id = :id AND r.status = :pendingStatus")
    int confirmIfPending(Long id, ReviewStatus confirmedStatus, String followUpNote,
                         String confirmer, java.time.LocalDateTime confirmTime,
                         ReviewStatus pendingStatus);
}
