package com.spring.transfer.service;

import com.spring.transfer.dto.BatchTransferRequest;
import com.spring.transfer.entity.ProductionLine;
import com.spring.transfer.entity.SpringArchive;
import com.spring.transfer.entity.TransferRecord;
import com.spring.transfer.repository.ProductionLineRepository;
import com.spring.transfer.repository.SpringArchiveRepository;
import com.spring.transfer.repository.TransferRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferRecordService {
    private final TransferRecordRepository transferRecordRepository;
    private final SpringArchiveRepository springArchiveRepository;
    private final ProductionLineRepository productionLineRepository;

    public List<TransferRecord> findBySpringId(Long springId) {
        return transferRecordRepository.findBySpringIdOrderByOperateTimeDesc(springId);
    }

    public Page<TransferRecord> findAll(Long springId, Long lineId, Pageable pageable) {
        return transferRecordRepository.findByCondition(springId, lineId, pageable);
    }

    @Transactional
    public List<TransferRecord> batchTransfer(BatchTransferRequest request) {
        ProductionLine toLine = productionLineRepository.findById(request.getToLineId())
                .orElseThrow(() -> new RuntimeException("目标产线不存在"));

        List<SpringArchive> springs = springArchiveRepository.findAllById(request.getSpringIds());
        if (springs.isEmpty()) {
            throw new RuntimeException("未找到有效的弹簧档案");
        }

        Map<Long, ProductionLine> lineCache = productionLineRepository.findAll().stream()
                .collect(Collectors.toMap(ProductionLine::getId, line -> line));

        List<TransferRecord> records = new ArrayList<>();
        for (SpringArchive spring : springs) {
            if (spring.getCurrentLineId().equals(request.getToLineId())) {
                continue;
            }

            ProductionLine fromLine = lineCache.get(spring.getCurrentLineId());
            if (fromLine == null) {
                continue;
            }

            TransferRecord record = new TransferRecord();
            record.setSpringId(spring.getId());
            record.setSpringCode(spring.getSpringCode());
            record.setFromLineId(fromLine.getId());
            record.setFromLineName(fromLine.getLineName());
            record.setToLineId(toLine.getId());
            record.setToLineName(toLine.getLineName());
            record.setOperator(request.getOperator());
            record.setOperateTime(LocalDateTime.now());
            record.setRemark(request.getRemark());

            records.add(transferRecordRepository.save(record));

            spring.setCurrentLineId(toLine.getId());
            springArchiveRepository.save(spring);
        }

        return records;
    }
}
