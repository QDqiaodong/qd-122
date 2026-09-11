package com.spring.transfer.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 负载告警事件响应：含触发时快照、处置信息与全部处置记录。
 */
@Data
public class LoadAlertEventResponse {
    private Long id;
    private String eventNo;
    private Long lineId;
    private String lineCode;
    private String lineName;

    /** 触发时级别：WARNING / OVERLOAD */
    private String alertLevel;

    /** 触发时的负载快照（与看板 LineLoadStats 同构） */
    private LineLoadStats snapshot;

    /** 处置状态：PENDING / PROCESSING / RESOLVED */
    private String status;

    private String responsiblePerson;
    private String handlePlan;
    private String remark;

    /** 关闭方式：MANUAL / AUTO */
    private String closeType;
    private String closeRemark;
    private String closedBy;

    private LocalDateTime triggerTime;
    private LocalDateTime confirmTime;
    private LocalDateTime closeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 产线当前实时负载状态（NORMAL/WARNING/OVERLOAD），用于前端展示事件期间状态变化 */
    private String currentLineStatus;

    /** 全部处置记录，按操作时间正序 */
    private List<HandleLogView> logs;

    /**
     * 处置记录视图。
     */
    @Data
    public static class HandleLogView {
        private Long id;
        private String action;
        private String operator;
        private LocalDateTime operateTime;
        private String resultStatus;
        private String detail;
    }
}
