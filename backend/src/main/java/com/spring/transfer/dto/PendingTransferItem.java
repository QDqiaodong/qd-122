package com.spring.transfer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 复核单上「次日必须跟进的待批划转」单条明细（签发时快照） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingTransferItem {
    /** 申请单ID */
    private Long applicationId;
    /** 申请单号 */
    private String applicationNo;
    /** 申请人 */
    private String applicant;
    /** 弹簧编号 */
    private String springCode;
    /** 弹簧型号 */
    private String model;
    /** 弹力系数 */
    private java.math.BigDecimal elasticCoefficient;
    /** 划出产线名称 */
    private String fromLineName;
    /** 申请原因 */
    private String reason;
    /** 申请时间（yyyy-MM-dd HH:mm:ss） */
    private String applyTime;
    /** 是否加急 */
    private boolean urgent;
}
