package com.spring.transfer.entity;

import com.spring.transfer.common.SealStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "spring_archive")
public class SpringArchive {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spring_code", nullable = false, unique = true, length = 32)
    private String springCode;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "elastic_coefficient", nullable = false, precision = 10, scale = 4)
    private BigDecimal elasticCoefficient;

    @Column(name = "outer_diameter", nullable = false, precision = 10, scale = 4)
    private BigDecimal outerDiameter;

    @Column(name = "current_line_id", nullable = false)
    private Long currentLineId;

    @Transient
    private String currentLineName;

    @Column(name = "initial_line_id", nullable = false)
    private Long initialLineId;

    @Transient
    private String initialLineName;

    /** 封存状态：NONE-正常 SEALED-封存中；存量行由列默认值 'NONE' 兜底 */
    @Enumerated(EnumType.STRING)
    @Column(name = "seal_status", nullable = false, length = 16)
    @ColumnDefault("'NONE'")
    private SealStatus sealStatus = SealStatus.NONE;

    @Column(name = "seal_reason")
    private String sealReason;

    @Column(name = "seal_expected_unseal_date")
    private LocalDate sealExpectedUnsealDate;

    @Column(name = "seal_operator", length = 32)
    private String sealOperator;

    @Column(name = "seal_time")
    private LocalDateTime sealTime;

    @Column(name = "unseal_operator", length = 32)
    private String unsealOperator;

    @Column(name = "unseal_time")
    private LocalDateTime unsealTime;

    @Column(name = "unseal_conclusion")
    private String unsealConclusion;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /** 是否处于封存中（历史数据可能为 null，按正常处理） */
    public boolean isSealed() {
        return sealStatus == SealStatus.SEALED;
    }

    /**
     * 黄标：存在偏离且未闭环的弹力抽检留样。标记实时按留样单重算（不落库），
     * 偏离件闭环处置后自动摘标；黄标件不能勾进划转申请。
     */
    @Transient
    private Boolean yellowFlag = false;

    /** 未闭环偏离留样条数（黄标来源数） */
    @Transient
    private Integer openDeviationCount = 0;

    public boolean isYellowFlagged() {
        return Boolean.TRUE.equals(yellowFlag);
    }

    /**
     * 封存信息摘要，用于拦截提示等需要明确原因的场景，
     * 例如：SP-2024-0001（封存原因：抽检不合格，预计解封日：2026-09-20）
     */
    public String getSealSummary() {
        StringBuilder sb = new StringBuilder(springCode).append("（封存原因：")
                .append(sealReason == null ? "未登记" : sealReason);
        if (sealExpectedUnsealDate != null) {
            sb.append("，预计解封日：").append(sealExpectedUnsealDate);
        }
        return sb.append("）").toString();
    }
}
