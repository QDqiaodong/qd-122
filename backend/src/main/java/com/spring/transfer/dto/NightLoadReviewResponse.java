package com.spring.transfer.dto;

import com.spring.transfer.entity.NightLoadReview;
import lombok.Data;
import java.util.List;

/** 夜班承载复核单详情：复核单本体 + 反序列化后的待批划转快照明细 */
@Data
public class NightLoadReviewResponse {
    private NightLoadReview review;
    private List<PendingTransferItem> pendingTransfers;
}
