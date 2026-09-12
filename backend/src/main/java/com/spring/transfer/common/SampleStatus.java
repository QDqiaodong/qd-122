package com.spring.transfer.common;

/**
 * 弹力抽检留样单闭环状态
 */
public enum SampleStatus {
    /** 待闭环：偏离件在闭环前挂黄标，不能勾进划转申请 */
    OPEN,
    /** 已闭环：必须填写处置结论，闭环后实测系数不可再改 */
    CLOSED
}
