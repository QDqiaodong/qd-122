package com.spring.transfer.service;

import com.spring.transfer.common.ItemStatus;
import com.spring.transfer.common.LineHaltStatus;
import com.spring.transfer.dto.LineHaltGuardResponse;
import com.spring.transfer.dto.LineHaltRequest;
import com.spring.transfer.dto.LineResumeRequest;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.TransferApplicationItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductionLineService {
    private final ProductionLineRepository productionLineRepository;
    private final TransferApplicationItemRepository applicationItemRepository;

    public List<ProductionLine> findAll() {
        return productionLineRepository.findAll();
    }

    public Optional<ProductionLine> findById(Long id) {
        return productionLineRepository.findById(id);
    }

    public Optional<ProductionLine> findByLineCode(String lineCode) {
        return productionLineRepository.findByLineCode(lineCode);
    }

    @Transactional
    public ProductionLine save(ProductionLine productionLine) {
        if (productionLineRepository.existsByLineCode(productionLine.getLineCode())) {
            throw new RuntimeException("产线编码已存在");
        }
        return productionLineRepository.save(productionLine);
    }

    /**
     * 登记停台前的影响提示：返回停台状态与流向该产线的待审批申请量。
     * 已有待审批单时前端必须二次确认，避免调度员在不知情的情况下停台。
     */
    public LineHaltGuardResponse getHaltGuard(Long lineId) {
        ProductionLine line = productionLineRepository.findById(lineId)
                .orElseThrow(() -> new RuntimeException("产线不存在"));
        LineHaltGuardResponse response = new LineHaltGuardResponse();
        response.setLineId(line.getId());
        response.setLineCode(line.getLineCode());
        response.setLineName(line.getLineName());
        response.setHalted(line.isHalted());
        response.setPendingItemCount(
                applicationItemRepository.countByToLineIdAndStatus(lineId, ItemStatus.PENDING));
        response.setPendingApplicationCount(
                applicationItemRepository.countDistinctApplicationByToLineIdAndStatus(lineId, ItemStatus.PENDING));
        return response;
    }

    /**
     * 登记临时停台：填写停台原因与预计复台时间。停台期间该产线不能作为划转接收方。
     * 重复停台被拒绝并回显当前停台信息；预计复台时间必须晚于当前时间。
     */
    @Transactional
    public ProductionLine halt(Long lineId, LineHaltRequest request) {
        ProductionLine line = productionLineRepository.findByIdForUpdate(lineId)
                .orElseThrow(() -> new RuntimeException("产线不存在"));
        if (line.isHalted()) {
            throw new RuntimeException("产线「" + line.getLineName() + "」已处于停台状态，请勿重复登记；"
                    + "当前停台信息：" + line.getHaltSummary());
        }
        if (request.getExpectedResumeTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("预计复台时间不能早于当前时间");
        }
        line.setHaltStatus(LineHaltStatus.HALTED);
        line.setHaltReason(request.getReason().trim());
        line.setHaltExpectedResumeTime(request.getExpectedResumeTime());
        line.setHaltOperator(request.getOperator().trim());
        line.setHaltTime(LocalDateTime.now());
        // 新一轮停台清空上一轮复台信息，避免展示过期结论
        line.setResumeOperator(null);
        line.setResumeTime(null);
        line.setResumeConclusion(null);
        return productionLineRepository.save(line);
    }

    /**
     * 复台：必须填写复台结论（停台原因核实/处置结果）。复台后该产线恢复可作为划转接收方。
     * 停台登记信息保留在产线上作为最近一次停台记录，便于追溯。
     */
    @Transactional
    public ProductionLine resume(Long lineId, LineResumeRequest request) {
        ProductionLine line = productionLineRepository.findByIdForUpdate(lineId)
                .orElseThrow(() -> new RuntimeException("产线不存在"));
        if (!line.isHalted()) {
            throw new RuntimeException("产线「" + line.getLineName() + "」未处于停台状态，无需复台");
        }
        line.setHaltStatus(LineHaltStatus.NORMAL);
        line.setResumeOperator(request.getOperator().trim());
        line.setResumeTime(LocalDateTime.now());
        line.setResumeConclusion(request.getConclusion().trim());
        return productionLineRepository.save(line);
    }
}
