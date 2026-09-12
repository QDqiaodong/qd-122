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

    /**
     * 加急待批数：全部加急申请单下「剩余待审批明细行」之和。
     * 与审批台明细行口径完全一致——部分处理单的已处理行不再占用加急名额
     */
    private Integer urgentPendingCount;

    /** 仍含剩余待审批明细行的加急申请单数（审批台「仅加急」列表可见的单量） */
    private Integer urgentPendingApplicationCount;

    /** 仍带加急标记但已无剩余待审批明细行的申请单数（结案未自动解除加急等异常残留） */
    private Integer urgentStaleCount;

    /**
     * 看板计数与审批台剩余待批口径是否一致。
     * false 时 urgentPendingMismatchReasons 给出具体对不齐原因，刷新看板即重新校验
     */
    private Boolean urgentPendingAligned;

    /** 加急待批计数对不齐的明确原因（正常对齐时为空） */
    private List<String> urgentPendingMismatchReasons;

    /** 全部产线负载概览，按 超载 > 预警 > 正常、负载率降序排列 */
    private List<LineLoadStats> lines;

    /** 按状态分组后的产线列表，供首页分组展示 */
    private List<LineLoadStats> normalLines;
    private List<LineLoadStats> warningLines;
    private List<LineLoadStats> overloadLines;
}
