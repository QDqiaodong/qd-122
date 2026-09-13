package com.spring.transfer.entity;

import com.spring.transfer.common.MeterShift;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 产线电表抄录：每条产线每个班次（白班/夜班）留下一条电表读数，
 * 记录班次、读数、抄表人与抄表时间。与上一条抄录相比，读数跳变绝对值超过约定幅度
 * （{@link #JUMP_THRESHOLD_KWH}）时本条标记为异常。
 *
 * 夜班复核提交前，该产线当天必须已有白班抄录，否则不能提交
 * （见 NightLoadReviewService 的白班抄录守卫）。
 */
@Data
@Entity
@Table(name = "meter_reading",
        uniqueConstraints = @UniqueConstraint(name = "uk_line_date_shift",
                columnNames = {"line_id", "reading_date", "shift"}))
public class MeterReading {
    /** 约定跳变幅度（kWh）：与上一条读数之差的绝对值超过该值即标记异常 */
    public static final BigDecimal JUMP_THRESHOLD_KWH = new BigDecimal("1000");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 抄表编号，如 MR20260913080000001 */
    @Column(name = "reading_no", nullable = false, unique = true, length = 32)
    private String readingNo;

    /** 产线ID */
    @Column(name = "line_id", nullable = false)
    private Long lineId;

    /** 产线编码（快照） */
    @Column(name = "line_code", nullable = false, length = 32)
    private String lineCode;

    /** 产线名称（快照） */
    @Column(name = "line_name", nullable = false, length = 64)
    private String lineName;

    /** 抄表所属日期（按服务端当前日期） */
    @Column(name = "reading_date", nullable = false)
    private LocalDate readingDate;

    /** 班次：DAY-白班 NIGHT-夜班 */
    @Enumerated(EnumType.STRING)
    @Column(name = "shift", nullable = false, length = 16)
    private MeterShift shift;

    /** 电表读数（kWh，累计读数，只增；异常时系统仍如实保存并标异常） */
    @Column(name = "reading_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal readingValue;

    /** 抄表人（电工/抄表员） */
    @Column(name = "reader", nullable = false, length = 32)
    private String reader;

    /** 抄表时间（登记时间） */
    @Column(name = "read_time", nullable = false)
    private LocalDateTime readTime;

    /** 与上一条抄录的读数差（kWh，本条读数-上一条读数，首条为 null） */
    @Column(name = "previous_value", precision = 12, scale = 2)
    private BigDecimal previousValue;

    /** 与上一条的读数差（kWh，首条为 null；读数回退时为负） */
    @Column(name = "jump_delta", precision = 12, scale = 2)
    private BigDecimal jumpDelta;

    /** 判定异常时使用的约定跳变幅度快照（kWh） */
    @Column(name = "jump_threshold", precision = 12, scale = 2)
    private BigDecimal jumpThreshold;

    /** 是否异常：与上一条读数跳变绝对值超过约定幅度（首条不标异常） */
    @Column(name = "abnormal", nullable = false)
    private Boolean abnormal = false;

    /** 异常原因说明（如：读数较上一条跳变 1500.00 kWh，超过约定幅度 1000 kWh） */
    @Column(name = "abnormal_reason", length = 255)
    private String abnormalReason;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 是否当日抄录（按抄表时间与服务端当前日期比较） */
    public boolean isToday() {
        return readTime != null && readTime.toLocalDate().equals(LocalDate.now());
    }
}
