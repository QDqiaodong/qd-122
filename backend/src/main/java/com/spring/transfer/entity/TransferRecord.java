package com.spring.transfer.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "transfer_record")
public class TransferRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spring_id", nullable = false)
    private Long springId;

    @Column(name = "spring_code", nullable = false, length = 32)
    private String springCode;

    @Column(name = "from_line_id", nullable = false)
    private Long fromLineId;

    @Column(name = "from_line_name", nullable = false, length = 64)
    private String fromLineName;

    @Column(name = "to_line_id", nullable = false)
    private Long toLineId;

    @Column(name = "to_line_name", nullable = false, length = 64)
    private String toLineName;

    @Column(name = "operator", nullable = false, length = 32)
    private String operator;

    @CreationTimestamp
    @Column(name = "operate_time", nullable = false)
    private LocalDateTime operateTime;

    @Column(name = "remark", length = 255)
    private String remark;
}
