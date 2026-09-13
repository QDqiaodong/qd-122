package com.spring.transfer.repository;

import com.spring.transfer.entity.LineInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface LineInspectionRepository extends JpaRepository<LineInspection, Long> {
    boolean existsByInspectionNo(String inspectionNo);

    /** 该产线最近一次点检（按点检时间、ID 倒序） */
    Optional<LineInspection> findTopByLineIdOrderByInspectTimeDescIdDesc(Long lineId);

    /** 该产线全部点检记录（点检时间倒序，最新的在前） */
    List<LineInspection> findByLineIdOrderByInspectTimeDescIdDesc(Long lineId);
}
