package com.spring.transfer.dto;

import com.spring.transfer.entity.TransferApplication;
import com.spring.transfer.entity.TransferApplicationItem;
import com.spring.transfer.entity.TransferApplicationLog;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class ApplicationDetailResponse {
    private TransferApplication application;
    private List<TransferApplicationItem> items;
    private List<TransferApplicationLog> logs;
}
