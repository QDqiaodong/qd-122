package com.spring.transfer.repository;

import com.spring.transfer.common.SampleStatus;
import com.spring.transfer.entity.ElasticSample;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ElasticSampleRepository extends JpaRepository<ElasticSample, Long> {

    boolean existsBySampleNo(String sampleNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ElasticSample s WHERE s.id = :id")
    Optional<ElasticSample> findByIdForUpdate(Long id);

    // 排序由 Controller 以 status DESC（枚举字符串 OPEN > CLOSED，待闭环优先）+ createTime DESC 传入
    @Query("SELECT s FROM ElasticSample s WHERE " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:lineId IS NULL OR s.lineId = :lineId) AND " +
           "(:deviated IS NULL OR s.deviated = :deviated) AND " +
           "(:keyword IS NULL OR s.sampleNo LIKE %:keyword% OR s.springCode LIKE %:keyword%)")
    Page<ElasticSample> findByCondition(SampleStatus status, Long lineId, Boolean deviated,
                                        String keyword, Pageable pageable);

    /** 一批弹簧的全部偏离待闭环留样（列表挂黄标计数 + 划转拦截提示） */
    List<ElasticSample> findBySpringIdInAndStatusAndDeviatedTrueOrderByIdAsc(Collection<Long> springIds, SampleStatus status);
}
