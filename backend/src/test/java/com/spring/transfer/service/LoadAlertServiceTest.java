package com.spring.transfer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.transfer.common.AlertStatus;
import com.spring.transfer.dto.AlertDispositionRequest;
import com.spring.transfer.dto.LineLoadStats;
import com.spring.transfer.dto.LoadStatus;
import com.spring.transfer.entity.LoadAlertEvent;
import com.spring.transfer.repository.LoadAlertEventRepository;
import com.spring.transfer.repository.LoadAlertHandleLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 负载告警事件同步与处置闭环单测：
 * 异常生成事件、恢复自动关闭、手动关闭后同一轮异常不重复、再次异常生成新事件；
 * 确认责任人进入处置中、关闭需填写处理说明。
 *
 * 单测直接调用 syncOne（绕过代理的 REQUIRES_NEW），因此事务管理器只需空实现。
 */
@ExtendWith(MockitoExtension.class)
class LoadAlertServiceTest {

    @Mock
    private LoadAlertEventRepository eventRepository;
    @Mock
    private LoadAlertHandleLogRepository handleLogRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    private LoadAlertService loadAlertService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        // 事务模板在单测中直接执行回调：getTransaction/commit/rollback 均为空动作
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        LoadAlertService proxy = mock(LoadAlertService.class);
        loadAlertService = spy(new LoadAlertService(eventRepository, handleLogRepository,
                objectMapper, transactionManager, proxy));
        // 绕过自代理 REQUIRES_NEW：直接执行同步逻辑
        lenient().doAnswer(inv -> {
            loadAlertService.syncOneInNewTransaction(inv.getArgument(0));
            return null;
        }).when(proxy).syncOneInNewTransaction(any());
        lenient().when(eventRepository.existsByEventNo(any())).thenReturn(false);
        lenient().when(eventRepository.saveAndFlush(any(LoadAlertEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(eventRepository.save(any(LoadAlertEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void warningLineWithoutOpenEventCreatesPendingEventWithSnapshot() {
        when(eventRepository.findByActiveLineId(1L)).thenReturn(Optional.empty());
        when(eventRepository.findFirstByLineIdOrderByTriggerTimeDescIdDesc(1L)).thenReturn(Optional.empty());

        loadAlertService.syncEvents(List.of(stats(1L, LoadStatus.WARNING.name())));

        ArgumentCaptor<LoadAlertEvent> captor = ArgumentCaptor.forClass(LoadAlertEvent.class);
        verify(eventRepository).saveAndFlush(captor.capture());
        LoadAlertEvent created = captor.getValue();
        assertEquals(1L, created.getLineId());
        assertEquals(LoadStatus.WARNING.name(), created.getAlertLevel());
        assertEquals(AlertStatus.PENDING, created.getStatus());
        assertEquals(1L, created.getActiveLineId());
        assertFalse(created.isManualCloseActive());
        assertNotNull(created.getSnapshotJson());
        assertTrue(created.getSnapshotJson().contains("WARNING"));
        assertTrue(created.getEventNo().startsWith("AE"));
    }

    @Test
    void normalLineWithOpenEventAutoResolvesIt() {
        LoadAlertEvent open = event(10L, 1L, AlertStatus.PROCESSING);
        when(eventRepository.findByActiveLineId(1L)).thenReturn(Optional.of(open));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(open));

        loadAlertService.syncEvents(List.of(stats(1L, LoadStatus.NORMAL.name())));

        assertEquals(AlertStatus.RESOLVED, open.getStatus());
        assertEquals("AUTO", open.getCloseType());
        assertEquals(LoadAlertService.SYSTEM_OPERATOR, open.getClosedBy());
        assertNotNull(open.getCloseTime());
        assertNull(open.getActiveLineId());
        verify(handleLogRepository).save(argThat(log ->
                "AUTO_RESOLVE".equals(log.getAction()) && log.getEventId().equals(10L)));
    }

    @Test
    void warningLineWithOpenEventDoesNotCreateDuplicate() {
        when(eventRepository.findByActiveLineId(1L))
                .thenReturn(Optional.of(event(10L, 1L, AlertStatus.PENDING)));

        loadAlertService.syncEvents(List.of(stats(1L, LoadStatus.WARNING.name())));

        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void manuallyClosedWhileAbnormalSuppressesNewEventUntilRecovery() {
        // 调度员在产线仍超载时手动关闭，事件保留抑制标记且 active_line_id 已置空
        LoadAlertEvent manuallyClosed = event(10L, 1L, AlertStatus.RESOLVED);
        manuallyClosed.setManualCloseActive(true);
        manuallyClosed.setActiveLineId(null);
        when(eventRepository.findByActiveLineId(1L)).thenReturn(Optional.empty());
        when(eventRepository.findFirstByLineIdOrderByTriggerTimeDescIdDesc(1L))
                .thenReturn(Optional.of(manuallyClosed));

        // 同一轮持续超载：不生成新事件
        loadAlertService.syncEvents(List.of(stats(1L, LoadStatus.OVERLOAD.name())));
        verify(eventRepository, never()).saveAndFlush(any());

        // 负载恢复正常：清除抑制标记，不建新事件
        when(eventRepository.findById(10L)).thenReturn(Optional.of(manuallyClosed));
        loadAlertService.syncEvents(List.of(stats(1L, LoadStatus.NORMAL.name())));
        assertFalse(manuallyClosed.isManualCloseActive());
        verify(eventRepository, never()).saveAndFlush(any());

        // 阈值下调/划转导致再次超载：生成新事件
        when(eventRepository.findFirstByLineIdOrderByTriggerTimeDescIdDesc(1L))
                .thenReturn(Optional.of(manuallyClosed));
        loadAlertService.syncEvents(List.of(stats(1L, LoadStatus.OVERLOAD.name())));
        verify(eventRepository, times(1)).saveAndFlush(any(LoadAlertEvent.class));
    }

    @Test
    void confirmRequiresResponsiblePersonAndPlan() {
        AlertDispositionRequest request = new AlertDispositionRequest();
        request.setAction("CONFIRM");
        request.setOperator("调度员A");
        // 责任人与处置计划的校验在加载事件之前，无需 stub 事件查询

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> loadAlertService.confirm(10L, request));
        assertTrue(ex.getMessage().contains("责任人"));
    }

    @Test
    void confirmMovesPendingEventToProcessingAndWritesLog() {
        LoadAlertEvent event = event(10L, 1L, AlertStatus.PENDING);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        AlertDispositionRequest request = new AlertDispositionRequest();
        request.setAction("CONFIRM");
        request.setOperator("调度员A");
        request.setResponsiblePerson("王工");
        request.setHandlePlan("今晚前划出2件至四号线");
        LoadAlertEvent result = loadAlertService.confirm(10L, request);

        assertEquals(AlertStatus.PROCESSING, result.getStatus());
        assertEquals("王工", result.getResponsiblePerson());
        assertEquals("今晚前划出2件至四号线", result.getHandlePlan());
        assertNotNull(result.getConfirmTime());
        verify(handleLogRepository).save(argThat(log ->
                "CONFIRM".equals(log.getAction())
                        && log.getEventId().equals(10L)
                        && log.getDetail().contains("王工")
                        && AlertStatus.PROCESSING.name().equals(log.getResultStatus())));
    }

    @Test
    void resolveRequiresRemarkAndMarksManualSuppressWhenStillAbnormal() {
        LoadAlertEvent event = event(10L, 1L, AlertStatus.PROCESSING);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        // 处理说明为空：即便产线仍超载，也必须先拦处理说明，而不是处置人/复核工号
        AlertDispositionRequest emptyRemark = new AlertDispositionRequest();
        emptyRemark.setAction("RESOLVE");
        emptyRemark.setOperator("调度员A");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> loadAlertService.resolve(10L, emptyRemark, LoadStatus.OVERLOAD.name()));
        assertTrue(ex.getMessage().contains("处理说明"));

        // 说明已补齐但处置人、复核工号都缺：先拦处置人，文案与看板完成处置表单一致
        AlertDispositionRequest onlyRemark = new AlertDispositionRequest();
        onlyRemark.setAction("RESOLVE");
        onlyRemark.setOperator("调度员A");
        onlyRemark.setRemark("已协调分流，观察中");
        RuntimeException missingPerson = assertThrows(RuntimeException.class,
                () -> loadAlertService.resolve(10L, onlyRemark, LoadStatus.OVERLOAD.name()));
        assertEquals("产线当前仍超载，完成处置必须填写处置人", missingPerson.getMessage());

        // 再补上处置人但缺复核工号：继续拦复核工号
        AlertDispositionRequest missingReview = new AlertDispositionRequest();
        missingReview.setAction("RESOLVE");
        missingReview.setOperator("调度员A");
        missingReview.setRemark("已协调分流，观察中");
        missingReview.setDisposePerson("王现场");
        RuntimeException missingReviewEx = assertThrows(RuntimeException.class,
                () -> loadAlertService.resolve(10L, missingReview, LoadStatus.OVERLOAD.name()));
        assertEquals("产线当前仍超载，完成处置必须填写复核工号", missingReviewEx.getMessage());

        // 三项填齐：仍超载也允许完成处置，处置人/复核工号随事件持久化并写入处置记录
        AlertDispositionRequest request = new AlertDispositionRequest();
        request.setAction("RESOLVE");
        request.setOperator("调度员A");
        request.setRemark("已协调分流，观察中");
        request.setDisposePerson("王现场");
        request.setReviewEmployeeNo("GH10086");
        LoadAlertEvent result = loadAlertService.resolve(10L, request, LoadStatus.OVERLOAD.name());
        assertEquals(AlertStatus.RESOLVED, result.getStatus());
        assertEquals("MANUAL", result.getCloseType());
        assertEquals("王现场", result.getDisposePerson());
        assertEquals("GH10086", result.getReviewEmployeeNo());
        assertTrue(result.isManualCloseActive());
        assertNull(result.getActiveLineId());
        assertNotNull(result.getCloseTime());
        verify(handleLogRepository).save(argThat(log ->
                "RESOLVE".equals(log.getAction())
                        && log.getEventId().equals(10L)
                        && log.getDetail().contains("处置人：王现场")
                        && log.getDetail().contains("复核工号：GH10086")));
    }

    @Test
    void resolveWhileWarningDoesNotRequireDisposePerson() {
        // 仅预警（非超载）时完成处置：有处理说明即可，不要求处置人/复核工号
        LoadAlertEvent event = event(10L, 1L, AlertStatus.PROCESSING);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        AlertDispositionRequest request = new AlertDispositionRequest();
        request.setAction("RESOLVE");
        request.setOperator("调度员A");
        request.setRemark("预警持续观察后关闭");
        LoadAlertEvent result = loadAlertService.resolve(10L, request, LoadStatus.WARNING.name());
        assertEquals(AlertStatus.RESOLVED, result.getStatus());
        assertTrue(result.isManualCloseActive());
        assertNull(result.getDisposePerson());
    }

    @Test
    void resolveWhenRecoveredDoesNotSuppressNextAlert() {
        LoadAlertEvent event = event(10L, 1L, AlertStatus.PROCESSING);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        AlertDispositionRequest request = new AlertDispositionRequest();
        request.setOperator("调度员A");
        request.setRemark("负载已回落，关闭");
        LoadAlertEvent result = loadAlertService.resolve(10L, request, LoadStatus.NORMAL.name());
        assertEquals(AlertStatus.RESOLVED, result.getStatus());
        assertFalse(result.isManualCloseActive());
    }

    @Test
    void confirmOnResolvedEventRejected() {
        LoadAlertEvent event = event(10L, 1L, AlertStatus.RESOLVED);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        AlertDispositionRequest request = new AlertDispositionRequest();
        request.setOperator("调度员A");
        request.setResponsiblePerson("王工");
        request.setHandlePlan("计划");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> loadAlertService.confirm(10L, request));
        assertTrue(ex.getMessage().contains("已关闭"));
    }

    private LineLoadStats stats(Long lineId, String status) {
        LineLoadStats stats = new LineLoadStats();
        stats.setLineId(lineId);
        stats.setLineCode("LINE-00" + lineId);
        stats.setLineName("装配" + lineId + "号线");
        stats.setStatus(status);
        stats.setSpringCount(3);
        stats.setReasons(status.equals(LoadStatus.NORMAL.name()) ? List.of() : List.of("超载"));
        return stats;
    }

    private LoadAlertEvent event(Long id, Long lineId, AlertStatus status) {
        LoadAlertEvent event = new LoadAlertEvent();
        event.setId(id);
        event.setEventNo("AE20260911000" + id);
        event.setLineId(lineId);
        event.setLineCode("LINE-00" + lineId);
        event.setLineName("装配" + lineId + "号线");
        event.setAlertLevel(LoadStatus.OVERLOAD.name());
        event.setSnapshotJson("{}");
        event.setStatus(status);
        if (status != AlertStatus.RESOLVED) {
            event.setActiveLineId(lineId);
        }
        return event;
    }
}
