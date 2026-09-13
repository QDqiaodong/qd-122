package com.spring.transfer.repository;

import com.spring.transfer.common.AlertStatus;
import com.spring.transfer.entity.LoadAlertEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoadAlertEventRepository extends JpaRepository<LoadAlertEvent, Long> {

    /** 某产线当前未关闭（待处理/处置中）的告警事件，同一产线至多一条 */
    Optional<LoadAlertEvent> findByActiveLineId(Long activeLineId);

    /** 批量取多条产线的未关闭事件（看板统计与卡片标记使用） */
    List<LoadAlertEvent> findByActiveLineIdIn(Collection<Long> activeLineIds);

    List<LoadAlertEvent> findByLineIdOrderByTriggerTimeDescIdDesc(Long lineId);

    /** 某产线最近一次告警事件（按触发时间倒序取第一条） */
    Optional<LoadAlertEvent> findFirstByLineIdOrderByTriggerTimeDescIdDesc(Long lineId);

    List<LoadAlertEvent> findByLineIdAndStatusOrderByTriggerTimeDescIdDesc(Long lineId, AlertStatus status);

    List<LoadAlertEvent> findAllByOrderByTriggerTimeDescIdDesc();

    List<LoadAlertEvent> findByStatusOrderByTriggerTimeDescIdDesc(AlertStatus status);

    /**
     * 批量取多条产线「已完成超载处置」的历史事件（含处置人/复核工号），
     * 看板按 closeTime 取每线最近一次展示。
     */
    List<LoadAlertEvent> findByLineIdInAndStatusAndAlertLevelOrderByCloseTimeDescIdDesc(
            Collection<Long> lineIds, AlertStatus status, String alertLevel);

    boolean existsByEventNo(String eventNo);

    long countByStatus(AlertStatus status);
}
