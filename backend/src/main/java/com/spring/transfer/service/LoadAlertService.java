package com.spring.transfer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.transfer.common.AlertStatus;
import com.spring.transfer.dto.LoadStatus;
import com.spring.transfer.dto.AlertDispositionRequest;
import com.spring.transfer.dto.LineLoadStats;
import com.spring.transfer.dto.LoadAlertEventResponse;
import com.spring.transfer.entity.LoadAlertEvent;
import com.spring.transfer.entity.LoadAlertHandleLog;
import com.spring.transfer.repository.LoadAlertEventRepository;
import com.spring.transfer.repository.LoadAlertHandleLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 产线负载告警事件与处置闭环服务。
 *
 * 事件随看板/明细的负载计算结果自动同步：
 * - 产线出现预警/超载且无未关闭事件时生成新事件并固化告警快照；
 * - 产线恢复正常时自动关闭未关闭事件；
 * - 调度员手动关闭时若产线仍异常，保留「手动关闭」标记，同一轮异常期间不重复建事件，
 *   待负载恢复正常后标记清除，再次触发才生成新事件（阈值调整或划转导致告警再次出现）。
 *
 * 每条事件记录触发快照、责任人、处置计划、备注、状态与处理时间，
 * 所有处置动作写入处置记录表，刷新后未处理事件仍可追踪。
 */
@Slf4j
@Service
public class LoadAlertService {
    public static final String SYSTEM_OPERATOR = "SYSTEM";

    private final LoadAlertEventRepository eventRepository;
    private final LoadAlertHandleLogRepository handleLogRepository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    /** 自注入代理：保证 REQUIRES_NEW 同步方法经代理调用时独立事务生效 */
    private final LoadAlertService self;

    public LoadAlertService(LoadAlertEventRepository eventRepository,
                            LoadAlertHandleLogRepository handleLogRepository,
                            ObjectMapper objectMapper,
                            PlatformTransactionManager transactionManager,
                            @Lazy @Autowired LoadAlertService self) {
        this.eventRepository = eventRepository;
        this.handleLogRepository = handleLogRepository;
        this.objectMapper = objectMapper;
        this.transactionManager = transactionManager;
        this.self = self;
    }

    /**
     * 按最新负载计算结果同步告警事件。看板与明细共用，保证「统计、分组、事件」三者同源。
     * 每条产线的建/关在独立事务中执行，并发刷新时唯一索引兜底，不影响看板整体返回。
     */
    public void syncEvents(List<LineLoadStats> statsList) {
        for (LineLoadStats stats : statsList) {
            try {
                self.syncOneInNewTransaction(stats);
            } catch (Exception e) {
                // 告警同步失败不应阻断看板/明细主流程
                log.error("产线 #{} 告警事件同步失败", stats.getLineId(), e);
            }
        }
    }

    /**
     * 单条产线的事件同步放在独立事务中：
     * 即使调用方处于划转审批事务内，同步异常也不会污染主事务（事件仅做补偿，看板刷新会再次同步）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncOneInNewTransaction(LineLoadStats stats) {
        syncOne(stats);
    }

    private void syncOne(LineLoadStats stats) {
        Long lineId = stats.getLineId();
        boolean abnormal = !LoadStatus.NORMAL.name().equals(stats.getStatus());
        Optional<LoadAlertEvent> openOpt = eventRepository.findByActiveLineId(lineId);

        if (abnormal) {
            if (openOpt.isPresent()) {
                return;
            }
            // 调度员在异常持续期间手动关闭的事件仍处于同一轮异常，不重复生成；
            // 待负载恢复正常清除标记后，再次异常才生成新事件
            LoadAlertEvent latest = eventRepository
                    .findFirstByLineIdOrderByTriggerTimeDescIdDesc(lineId).orElse(null);
            if (latest != null && latest.getStatus() == AlertStatus.RESOLVED
                    && latest.isManualCloseActive()) {
                return;
            }
            createEvent(stats);
        } else {
            openOpt.ifPresent(this::autoResolve);
            if (openOpt.isEmpty()) {
                // 负载恢复正常，清除手动关闭抑制标记，为下一轮告警放行
                LoadAlertEvent latest = eventRepository
                        .findFirstByLineIdOrderByTriggerTimeDescIdDesc(lineId).orElse(null);
                if (latest != null && latest.isManualCloseActive()) {
                    clearManualSuppress(latest.getId());
                }
            }
        }
    }

    private void createEvent(LineLoadStats stats) {
        LoadAlertEvent event = new LoadAlertEvent();
        event.setEventNo(generateEventNo());
        event.setLineId(stats.getLineId());
        event.setLineCode(stats.getLineCode());
        event.setLineName(stats.getLineName());
        event.setAlertLevel(stats.getStatus());
        event.setSnapshotJson(writeSnapshot(stats));
        event.setStatus(AlertStatus.PENDING);
        event.setTriggerTime(LocalDateTime.now());
        event.setActiveLineId(stats.getLineId());
        try {
            eventRepository.saveAndFlush(event);
        } catch (DuplicateKeyException e) {
            // 并发刷新：另一个请求已为该产线建出未关闭事件，直接复用
            log.debug("产线 #{} 未关闭告警事件已由并发请求创建", stats.getLineId());
        }
    }

    private void autoResolve(LoadAlertEvent event) {
        LoadAlertEvent locked = eventRepository.findById(event.getId()).orElse(null);
        if (locked == null || locked.getStatus() == AlertStatus.RESOLVED) {
            return;
        }
        closeEvent(locked, "AUTO", SYSTEM_OPERATOR, "负载指标已恢复正常，系统自动关闭", false);
        handleLogRepository.save(new LoadAlertHandleLog(locked.getId(), "AUTO_RESOLVE",
                SYSTEM_OPERATOR, AlertStatus.RESOLVED.name(),
                "产线负载恢复正常，事件自动关闭"));
    }

    private void clearManualSuppress(Long eventId) {
        LoadAlertEvent locked = eventRepository.findById(eventId).orElse(null);
        if (locked != null && locked.isManualCloseActive()) {
            locked.setManualCloseActive(false);
            eventRepository.save(locked);
        }
    }

    /**
     * 将未关闭事件标记挂到看板概览项上，并返回看板顶部的事件计数。
     */
    public Map<String, Integer> attachOpenEvents(List<LineLoadStats> statsList) {
        List<Long> lineIds = statsList.stream().map(LineLoadStats::getLineId).toList();
        Map<Long, LoadAlertEvent> openByLine = lineIds.isEmpty()
                ? Map.of()
                : eventRepository.findByActiveLineIdIn(lineIds).stream()
                        .collect(Collectors.toMap(LoadAlertEvent::getActiveLineId, Function.identity()));
        int pending = 0;
        for (LineLoadStats stats : statsList) {
            LoadAlertEvent event = openByLine.get(stats.getLineId());
            if (event != null) {
                stats.setOpenAlertEventId(event.getId());
                stats.setOpenAlertStatus(event.getStatus().name());
                if (event.getStatus() == AlertStatus.PENDING) {
                    pending++;
                }
            }
        }
        Map<String, Integer> counts = new HashMap<>();
        counts.put("open", openByLine.size());
        counts.put("pending", pending);
        return counts;
    }

    public Optional<LoadAlertEvent> findOpenEvent(Long lineId) {
        return eventRepository.findByActiveLineId(lineId);
    }

    public List<LoadAlertEvent> findLineEvents(Long lineId, AlertStatus status) {
        if (status != null) {
            return eventRepository.findByLineIdAndStatusOrderByTriggerTimeDescIdDesc(lineId, status);
        }
        return eventRepository.findByLineIdOrderByTriggerTimeDescIdDesc(lineId);
    }

    public List<LoadAlertEvent> findAllEvents(AlertStatus status, Long lineId) {
        if (status != null && lineId != null) {
            return eventRepository.findByLineIdAndStatusOrderByTriggerTimeDescIdDesc(lineId, status);
        }
        if (status != null) {
            return eventRepository.findByStatusOrderByTriggerTimeDescIdDesc(status);
        }
        if (lineId != null) {
            return eventRepository.findByLineIdOrderByTriggerTimeDescIdDesc(lineId);
        }
        return eventRepository.findAllByOrderByTriggerTimeDescIdDesc();
    }

    public LoadAlertEvent getEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("告警事件不存在"));
    }

    /**
     * 确认责任人与处置计划：待处理事件首次确认后进入处置中；处置中可补充/调整。
     */
    public LoadAlertEvent confirm(Long eventId, AlertDispositionRequest request) {
        String person = trim(request.getResponsiblePerson());
        String plan = trim(request.getHandlePlan());
        String remark = trim(request.getRemark());
        if (person.isEmpty()) {
            throw new RuntimeException("请先确认责任人");
        }
        if (plan.isEmpty()) {
            throw new RuntimeException("请填写处置计划");
        }
        String operator = request.getOperator().trim();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        return txTemplate.execute(status -> {
            LoadAlertEvent event = getEventOrThrow(eventId);
            if (event.getStatus() == AlertStatus.RESOLVED) {
                throw new RuntimeException("该告警事件已关闭，不能再确认处置");
            }
            boolean firstConfirm = event.getStatus() == AlertStatus.PENDING;
            event.setResponsiblePerson(person);
            event.setHandlePlan(plan);
            if (!remark.isEmpty()) {
                // 备注按时间线追加，保留每次处置说明（完整过程见处置记录表）
                String existing = event.getRemark();
                event.setRemark(existing == null || existing.isBlank()
                        ? remark
                        : existing + " | " + operator + "：" + remark);
            }
            event.setStatus(AlertStatus.PROCESSING);
            if (event.getConfirmTime() == null) {
                event.setConfirmTime(LocalDateTime.now());
            }
            event = eventRepository.save(event);

            String detail = (firstConfirm ? "确认责任人：" + person + "；处置计划：" + plan
                    : "更新责任人/处置计划：责任人 " + person + "；处置计划 " + plan)
                    + (remark.isEmpty() ? "" : "；备注：" + remark);
            handleLogRepository.save(new LoadAlertHandleLog(eventId,
                    firstConfirm ? "CONFIRM" : "PLAN", operator,
                    AlertStatus.PROCESSING.name(), detail));
            return event;
        });
    }

    /**
     * 标记处理完成并关闭事件。若处置时产线仍处于预警/超载，保留手动关闭抑制标记，
     * 同一轮持续异常期间不重复建事件；负载恢复正常后标记清除，再次触发会生成新事件。
     */
    public LoadAlertEvent resolve(Long eventId, AlertDispositionRequest request, String currentLineStatus) {
        String remark = trim(request.getRemark());
        if (remark.isEmpty()) {
            throw new RuntimeException("请填写处理说明后再关闭告警");
        }
        String operator = request.getOperator().trim();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        return txTemplate.execute(status -> {
            LoadAlertEvent event = getEventOrThrow(eventId);
            if (event.getStatus() == AlertStatus.RESOLVED) {
                throw new RuntimeException("该告警事件已关闭，请勿重复操作");
            }
            boolean stillAbnormal = !LoadStatus.NORMAL.name().equals(currentLineStatus);
            event.setRemark(remark);
            closeEvent(event, "MANUAL", operator, remark, stillAbnormal);
            handleLogRepository.save(new LoadAlertHandleLog(eventId, "RESOLVE", operator,
                    AlertStatus.RESOLVED.name(),
                    "调度员标记处理完成：" + remark
                            + (stillAbnormal ? "（处置时产线仍为"
                            + ("OVERLOAD".equals(currentLineStatus) ? "超载" : "预警")
                            + "状态，同一轮异常期间不再重复告警）" : "")));
            return event;
        });
    }

    private void closeEvent(LoadAlertEvent event, String closeType, String operator,
                            String closeRemark, boolean manualCloseActive) {
        event.setStatus(AlertStatus.RESOLVED);
        event.setCloseType(closeType);
        event.setClosedBy(operator);
        event.setCloseRemark(closeRemark);
        event.setCloseTime(LocalDateTime.now());
        event.setActiveLineId(null);
        event.setManualCloseActive(manualCloseActive);
        eventRepository.save(event);
    }

    // ---------------------------------------------------------------------
    // 视图组装
    // ---------------------------------------------------------------------

    public LoadAlertEventResponse toResponse(LoadAlertEvent event, String currentLineStatus) {
        LoadAlertEventResponse view = new LoadAlertEventResponse();
        view.setId(event.getId());
        view.setEventNo(event.getEventNo());
        view.setLineId(event.getLineId());
        view.setLineCode(event.getLineCode());
        view.setLineName(event.getLineName());
        view.setAlertLevel(event.getAlertLevel());
        view.setSnapshot(readSnapshot(event));
        view.setStatus(event.getStatus().name());
        view.setResponsiblePerson(event.getResponsiblePerson());
        view.setHandlePlan(event.getHandlePlan());
        view.setRemark(event.getRemark());
        view.setCloseType(event.getCloseType());
        view.setCloseRemark(event.getCloseRemark());
        view.setClosedBy(event.getClosedBy());
        view.setTriggerTime(event.getTriggerTime());
        view.setConfirmTime(event.getConfirmTime());
        view.setCloseTime(event.getCloseTime());
        view.setCreateTime(event.getCreateTime());
        view.setUpdateTime(event.getUpdateTime());
        view.setCurrentLineStatus(currentLineStatus);
        view.setLogs(toLogViews(handleLogRepository.findByEventIdOrderByOperateTimeAscIdAsc(event.getId())));
        return view;
    }

    /** 批量组装：处置记录一次性批量查出，避免 N+1 */
    public List<LoadAlertEventResponse> toResponses(List<LoadAlertEvent> events,
                                                    Map<Long, String> currentStatusByLine) {
        if (events.isEmpty()) {
            return List.of();
        }
        List<Long> eventIds = events.stream().map(LoadAlertEvent::getId).toList();
        Map<Long, List<LoadAlertHandleLog>> logsByEvent = handleLogRepository
                .findByEventIdInOrderByOperateTimeAscIdAsc(eventIds).stream()
                .collect(Collectors.groupingBy(LoadAlertHandleLog::getEventId));

        List<LoadAlertEventResponse> views = new ArrayList<>();
        for (LoadAlertEvent event : events) {
            LoadAlertEventResponse view = new LoadAlertEventResponse();
            view.setId(event.getId());
            view.setEventNo(event.getEventNo());
            view.setLineId(event.getLineId());
            view.setLineCode(event.getLineCode());
            view.setLineName(event.getLineName());
            view.setAlertLevel(event.getAlertLevel());
            view.setSnapshot(readSnapshot(event));
            view.setStatus(event.getStatus().name());
            view.setResponsiblePerson(event.getResponsiblePerson());
            view.setHandlePlan(event.getHandlePlan());
            view.setRemark(event.getRemark());
            view.setCloseType(event.getCloseType());
            view.setCloseRemark(event.getCloseRemark());
            view.setClosedBy(event.getClosedBy());
            view.setTriggerTime(event.getTriggerTime());
            view.setConfirmTime(event.getConfirmTime());
            view.setCloseTime(event.getCloseTime());
            view.setCreateTime(event.getCreateTime());
            view.setUpdateTime(event.getUpdateTime());
            view.setCurrentLineStatus(currentStatusByLine.get(event.getLineId()));
            view.setLogs(toLogViews(logsByEvent.getOrDefault(event.getId(), List.of())));
            views.add(view);
        }
        return views;
    }

    private List<LoadAlertEventResponse.HandleLogView> toLogViews(List<LoadAlertHandleLog> logs) {
        return logs.stream().map(log -> {
            LoadAlertEventResponse.HandleLogView view = new LoadAlertEventResponse.HandleLogView();
            view.setId(log.getId());
            view.setAction(log.getAction());
            view.setOperator(log.getOperator());
            view.setOperateTime(log.getOperateTime());
            view.setResultStatus(log.getResultStatus());
            view.setDetail(log.getDetail());
            return view;
        }).toList();
    }

    private String writeSnapshot(LineLoadStats stats) {
        try {
            return objectMapper.writeValueAsString(stats);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("告警快照序列化失败", e);
        }
    }

    private LineLoadStats readSnapshot(LoadAlertEvent event) {
        try {
            return objectMapper.readValue(event.getSnapshotJson(), LineLoadStats.class);
        } catch (JsonProcessingException e) {
            log.error("告警事件 #{} 快照反序列化失败", event.getId(), e);
            return null;
        }
    }

    private String generateEventNo() {
        String no;
        do {
            no = "AE" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        } while (eventRepository.existsByEventNo(no));
        return no;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
