package com.spring.transfer.dto;

import lombok.Data;
import java.util.List;

/**
 * 负载预警看板汇总：顶部统计数字与下方分组列表使用同一份数据，
 * 保证刷新后统计与列表始终一致。
 */
@Data
public class LineLoadBoardResponse {
    /** 产线总数 */
    private Integer totalLines;

    /** 弹簧总数（按当前归属统计） */
    private Integer totalSprings;

    private Integer normalCount;
    private Integer warningCount;
    private Integer overloadCount;

    /** 待处理（未确认责任人）的告警事件数 */
    private Integer pendingAlertCount;

    /** 未关闭（待处理 + 处置中）的告警事件数 */
    private Integer openAlertCount;

    /** 全部产线负载概览，按 超载 > 预警 > 正常、负载率降序排列 */
    private List<LineLoadStats> lines;

    /** 按状态分组后的产线列表，供首页分组展示 */
    private List<LineLoadStats> normalLines;
    private List<LineLoadStats> warningLines;
    private List<LineLoadStats> overloadLines;
}
