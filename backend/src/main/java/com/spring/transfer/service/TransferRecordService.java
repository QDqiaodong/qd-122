package com.spring.transfer.service;

import com.spring.transfer.entity.TransferRecord;
import com.spring.transfer.repository.TransferRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferRecordService {
    private final TransferRecordRepository transferRecordRepository;

    public List<TransferRecord> findBySpringId(Long springId) {
        return transferRecordRepository.findBySpringIdOrderByOperateTimeDesc(springId);
    }

    public Page<TransferRecord> findAll(Long springId, Long lineId, Pageable pageable) {
        return transferRecordRepository.findByCondition(springId, lineId, pageable);
    }
}
