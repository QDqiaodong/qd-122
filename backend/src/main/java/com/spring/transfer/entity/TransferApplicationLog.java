package com.spring.transfer.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "transfer_application_log")
public class TransferApplicationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    /** 操作类型：SUBMIT-提交申请 APPROVE-审批通过 REJECT-审批驳回 */
    @Column(name = "action", nullable = false, length = 16)
    private String action;

    @Column(name = "operator", nullable = false, length = 32)
    private String operator;

    @CreationTimestamp
    @Column(name = "operate_time", nullable = false)
    private LocalDateTime operateTime;

    @Column(name = "detail", length = 512)
    private String detail;

    public TransferApplicationLog() {
    }

    public TransferApplicationLog(Long applicationId, String action, String operator, String detail) {
        this.applicationId = applicationId;
        this.action = action;
        this.operator = operator;
        this.detail = detail;
    }
}
