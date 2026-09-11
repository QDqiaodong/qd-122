package com.spring.transfer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.transfer.common.SimulationStatus;
import com.spring.transfer.dto.AdoptSimulationRequest;
import com.spring.transfer.dto.SaveSimulationRequest;
import com.spring.transfer.dto.SimulateRequest;
import com.spring.transfer.dto.SimulationDetailResponse;
import com.spring.transfer.dto.SimulationEstimateResponse;
import com.spring.transfer.dto.SubmitApplicationRequest;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.entity.SpringArchive;
import com.spring.transfer.entity.TransferApplication;
import com.spring.transfer.entity.TransferSimulation;
import com.spring.transfer.entity.TransferSimulationItem;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.SpringArchiveRepository;
import com.spring.transfer.repository.TransferApplicationRepository;
import com.spring.transfer.repository.TransferSimulationItemRepository;
import com.spring.transfer.repository.TransferSimulationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工序调拨模拟服务。
 *
 * 调度员选择一组弹簧和拟接收产线后，先预估各产线划出/划入后的负载（与负载预警看板同一口径），
 * 可将预估结果保存为模拟方案；方案可标记采用或作废。
 * 采用时基于方案明细生成待审批划转申请（复用划转申请的全部校验与并发保护），
 * 方案状态、关联申请状态与负载预估快照均持久化，刷新后保持一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferSimulationService {
    private final TransferSimulationRepository simulationRepository;
    private final TransferSimulationItemRepository itemRepository;
    private final TransferApplicationRepository applicationRepository;
    private final SpringArchiveRepository springArchiveRepository;
    private final ProductionLineRepository productionLineRepository;
    private final LineLoadService lineLoadService;
    private final TransferApplicationService applicationService;
    private final ObjectMapper objectMapper;

    /** 实时预估：不保存方案，直接返回各受影响产线的负载预估 */
    public SimulationEstimateResponse preview(SimulateRequest request) {
        return lineLoadService.simulateTransfer(request.getSpringIds(), request.getToLineId());
    }

    /**
     * 保存模拟方案：按当前数据计算负载预估并随方案持久化快照，
     * 之后查看/刷新时展示的预估结果与保存时一致
     */
    @Transactional
    public TransferSimulation save(SaveSimulationRequest request) {
        SimulationEstimateResponse estimate = lineLoadService.simulateTransfer(
                request.getSpringIds(), request.getToLineId());

        List<Long> springIds = request.getSpringIds().stream().distinct().toList();
        Map<Long, SpringArchive> springMap = springArchiveRepository.findAllById(springIds).stream()
                .collect(Collectors.toMap(SpringArchive::getId, Function.identity()));
        Map<Long, String> lineNameMap = productionLineRepository.findAll().stream()
                .collect(Collectors.toMap(ProductionLine::getId, ProductionLine::getLineName));

        TransferSimulation simulation = new TransferSimulation();
        simulation.setSimulationNo(generateSimulationNo());
        simulation.setOperator(request.getOperator().trim());
        simulation.setToLineId(estimate.getToLineId());
        simulation.setToLineName(estimate.getToLineName());
        simulation.setRemark(request.getRemark() == null || request.getRemark().trim().isEmpty()
                ? null : request.getRemark().trim());
        simulation.setStatus(SimulationStatus.DRAFT);
        simulation.setEstimateSnapshot(writeSnapshot(estimate));
        simulation = simulationRepository.save(simulation);

        List<TransferSimulationItem> items = new ArrayList<>();
        for (Long springId : springIds) {
            SpringArchive spring = springMap.get(springId);
            if (spring == null) {
                throw new RuntimeException("弹簧档案不存在，ID: " + springId);
            }
            TransferSimulationItem item = new TransferSimulationItem();
            item.setSimulationId(simulation.getId());
            item.setSpringId(spring.getId());
            item.setSpringCode(spring.getSpringCode());
            item.setModel(spring.getModel());
            item.setElasticCoefficient(spring.getElasticCoefficient());
            item.setFromLineId(spring.getCurrentLineId());
            item.setFromLineName(lineNameMap.get(spring.getCurrentLineId()));
            items.add(itemRepository.save(item));
        }

        fillExtras(simulation);
        return simulation;
    }

    public Page<TransferSimulation> findAll(SimulationStatus status, String keyword, Pageable pageable) {
        Page<TransferSimulation> page = simulationRepository.findByCondition(status, keyword, pageable);
        page.getContent().forEach(this::fillExtras);
        return page;
    }

    public SimulationDetailResponse getDetail(Long id) {
        TransferSimulation simulation = simulationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模拟方案不存在"));
        fillExtras(simulation);
        List<TransferSimulationItem> items = itemRepository.findBySimulationIdOrderByIdAsc(id);
        return new SimulationDetailResponse(simulation, items, readSnapshot(simulation.getEstimateSnapshot()));
    }

    /**
     * 采用方案：基于方案明细生成待审批划转申请。
     * 方案行加悲观锁，防止并发采用生成重复申请；划转申请提交失败时整体回滚，方案保持草稿状态。
     */
    @Transactional
    public TransferSimulation adopt(Long id, AdoptSimulationRequest request) {
        TransferSimulation simulation = simulationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("模拟方案不存在"));
        if (simulation.getStatus() == SimulationStatus.ADOPTED) {
            throw new RuntimeException("该方案已采用（申请单号 " + simulation.getApplicationNo() + "），请勿重复操作");
        }
        if (simulation.getStatus() == SimulationStatus.DISCARDED) {
            throw new RuntimeException("该方案已作废，不能采用");
        }

        List<TransferSimulationItem> items = itemRepository.findBySimulationIdOrderByIdAsc(id);
        if (items.isEmpty()) {
            throw new RuntimeException("方案明细为空，无法生成划转申请");
        }

        SubmitApplicationRequest submit = new SubmitApplicationRequest();
        submit.setSpringIds(items.stream().map(TransferSimulationItem::getSpringId).toList());
        submit.setToLineId(simulation.getToLineId());
        submit.setApplicant(request.getApplicant().trim());
        submit.setReason(request.getReason() == null || request.getReason().trim().isEmpty()
                ? "调拨模拟方案 " + simulation.getSimulationNo() + " 经负载预估后采用"
                : request.getReason().trim());
        TransferApplication application = applicationService.submit(submit);

        simulation.setStatus(SimulationStatus.ADOPTED);
        simulation.setApplicationId(application.getId());
        simulation.setApplicationNo(application.getApplicationNo());
        simulation = simulationRepository.save(simulation);
        fillExtras(simulation);
        return simulation;
    }

    /** 作废方案：仅草稿状态可作废，已采用的方案不允许作废 */
    @Transactional
    public TransferSimulation discard(Long id) {
        TransferSimulation simulation = simulationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("模拟方案不存在"));
        if (simulation.getStatus() == SimulationStatus.ADOPTED) {
            throw new RuntimeException("该方案已采用并生成划转申请，不能作废");
        }
        if (simulation.getStatus() == SimulationStatus.DISCARDED) {
            throw new RuntimeException("该方案已作废，请勿重复操作");
        }
        simulation.setStatus(SimulationStatus.DISCARDED);
        return simulationRepository.save(simulation);
    }

    /** 补全明细数量与关联申请单的实时状态，保证列表/详情刷新后申请状态一致 */
    private void fillExtras(TransferSimulation simulation) {
        simulation.setItemCount(itemRepository.countBySimulationId(simulation.getId()));
        if (simulation.getApplicationId() != null) {
            applicationRepository.findById(simulation.getApplicationId())
                    .ifPresent(app -> simulation.setApplicationStatus(app.getStatus().name()));
        }
    }

    private String writeSnapshot(SimulationEstimateResponse estimate) {
        try {
            return objectMapper.writeValueAsString(estimate);
        } catch (Exception e) {
            throw new RuntimeException("负载预估快照序列化失败");
        }
    }

    private SimulationEstimateResponse readSnapshot(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, SimulationEstimateResponse.class);
        } catch (Exception e) {
            log.error("解析模拟方案负载预估快照失败", e);
            return null;
        }
    }

    private String generateSimulationNo() {
        String no;
        do {
            no = "SIM" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        } while (simulationRepository.existsBySimulationNo(no));
        return no;
    }
}
