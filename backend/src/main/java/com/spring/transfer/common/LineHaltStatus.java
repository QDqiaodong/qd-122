package com.spring.transfer.common;

/**
 * 产线临时停台状态
 */
public enum LineHaltStatus {
    /** 正常（未停台） */
    NORMAL,
    /** 停台中（临时停台，期间不能作为划转接收方） */
    HALTED
}
