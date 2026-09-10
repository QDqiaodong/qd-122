package com.spring.transfer.dto;

import com.spring.transfer.entity.SpringArchive;
import com.spring.transfer.entity.TransferRecord;
import lombok.Data;
import java.util.List;

/**
 * 单条产线负载明细：概览信息 + 当前归属弹簧明细 + 近期划转趋势流水。
 */
@Data
public class LineLoadDetailResponse {
    private LineLoadStats stats;

    /** 当前归属该产线的全部弹簧明细 */
    private List<SpringArchive> springs;

    /** 近 trendDays 天与该产线相关的划转流水（划入 + 划出），按时间倒序 */
    private List<TransferRecord> recentTransfers;
}
