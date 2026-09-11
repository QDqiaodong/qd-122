package com.spring.transfer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.transfer.common.ApplicationStatus;
import com.spring.transfer.common.SimulationStatus;
import com.spring.transfer.dto.AdoptSimulationRequest;
import com.spring.transfer.entity.TransferApplication;
import com.spring.transfer.entity.TransferSimulation;
import com.spring.transfer.entity.TransferSimulationItem;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.SpringArchiveRepository;
import com.spring.transfer.repository.TransferApplicationRepository;
import com.spring.transfer.repository.TransferSimulationItemRepository;
import com.spring.transfer.repository.TransferSimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 模拟方案状态流转单测：采用生成待审批申请、重复采用/作废的防护。
 */
@ExtendWith(MockitoExtension.class)
class TransferSimulationServiceTest {

    @Mock
    private TransferSimulationRepository simulationRepository;
    @Mock
    private TransferSimulationItemRepository itemRepository;
    @Mock
    private TransferApplicationRepository applicationRepository;
    @Mock
    private SpringArchiveRepository springArchiveRepository;
    @Mock
    private ProductionLineRepository productionLineRepository;
    @Mock
    private LineLoadService lineLoadService;
    @Mock
    private TransferApplicationService applicationService;

    private TransferSimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new TransferSimulationService(
                simulationRepository, itemRepository, applicationRepository,
                springArchiveRepository, productionLineRepository,
                lineLoadService, applicationService, new ObjectMapper());
        lenient().when(simulationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(itemRepository.countBySimulationId(any())).thenReturn(1);
    }

    @Test
    void adoptGeneratesPendingApplicationAndMarksPlan() {
        TransferSimulation simulation = simulation(SimulationStatus.DRAFT);
        when(simulationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(simulation));
        when(itemRepository.findBySimulationIdOrderByIdAsc(1L)).thenReturn(List.of(item(11L)));

        TransferApplication application = new TransferApplication();
        application.setId(100L);
        application.setApplicationNo("TA202609110001");
        application.setStatus(ApplicationStatus.PENDING);
        when(applicationService.submit(any())).thenReturn(application);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        AdoptSimulationRequest request = new AdoptSimulationRequest();
        request.setApplicant("张三");
        TransferSimulation result = simulationService.adopt(1L, request);

        assertEquals(SimulationStatus.ADOPTED, result.getStatus());
        assertEquals(100L, result.getApplicationId());
        assertEquals("TA202609110001", result.getApplicationNo());
        assertEquals(ApplicationStatus.PENDING.name(), result.getApplicationStatus());
    }

    @Test
    void adoptRejectsRepeatedAdoption() {
        TransferSimulation simulation = simulation(SimulationStatus.ADOPTED);
        simulation.setApplicationNo("TA202609110001");
        when(simulationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(simulation));

        AdoptSimulationRequest request = new AdoptSimulationRequest();
        request.setApplicant("张三");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> simulationService.adopt(1L, request));
        assertTrue(ex.getMessage().contains("已采用"));
    }

    @Test
    void adoptRejectsDiscardedPlan() {
        TransferSimulation simulation = simulation(SimulationStatus.DISCARDED);
        when(simulationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(simulation));

        AdoptSimulationRequest request = new AdoptSimulationRequest();
        request.setApplicant("张三");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> simulationService.adopt(1L, request));
        assertTrue(ex.getMessage().contains("已作废"));
    }

    @Test
    void discardMarksDraftPlan() {
        TransferSimulation simulation = simulation(SimulationStatus.DRAFT);
        when(simulationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(simulation));

        TransferSimulation result = simulationService.discard(1L);
        assertEquals(SimulationStatus.DISCARDED, result.getStatus());
    }

    @Test
    void discardRejectsAdoptedPlan() {
        TransferSimulation simulation = simulation(SimulationStatus.ADOPTED);
        when(simulationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(simulation));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> simulationService.discard(1L));
        assertTrue(ex.getMessage().contains("不能作废"));
    }

    private TransferSimulation simulation(SimulationStatus status) {
        TransferSimulation simulation = new TransferSimulation();
        simulation.setId(1L);
        simulation.setSimulationNo("SIM202609110001");
        simulation.setOperator("调度员");
        simulation.setToLineId(4L);
        simulation.setToLineName("装配四号线");
        simulation.setStatus(status);
        return simulation;
    }

    private TransferSimulationItem item(Long springId) {
        TransferSimulationItem item = new TransferSimulationItem();
        item.setSimulationId(1L);
        item.setSpringId(springId);
        item.setSpringCode("SP-2024-0001");
        item.setModel("C-Spring-05");
        item.setFromLineId(1L);
        item.setFromLineName("装配一号线");
        return item;
    }
}
