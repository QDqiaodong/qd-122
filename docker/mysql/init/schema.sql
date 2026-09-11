SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS spring_transfer DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE spring_transfer;

CREATE TABLE IF NOT EXISTS production_line (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    line_code VARCHAR(32) NOT NULL UNIQUE COMMENT '产线编码',
    line_name VARCHAR(64) NOT NULL COMMENT '产线名称',
    description VARCHAR(255) COMMENT '产线描述',
    daily_capacity_threshold INT COMMENT '日承载阈值（单日可承载弹簧数量上限）',
    elastic_min DECIMAL(10,4) COMMENT '适用弹力系数下限 N/mm',
    elastic_max DECIMAL(10,4) COMMENT '适用弹力系数上限 N/mm',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_line_code (line_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产产线表';

-- 兼容已初始化的旧库：补齐负载阈值相关列（列已存在时忽略报错）
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，容器初始化时本脚本仅在首次建库执行，
-- 存量库由 JPA ddl-auto=update 自动补列，LineThresholdInitializer 补默认值。

CREATE TABLE IF NOT EXISTS spring_archive (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    spring_code VARCHAR(32) NOT NULL UNIQUE COMMENT '弹簧编号',
    model VARCHAR(64) NOT NULL COMMENT '弹簧型号',
    elastic_coefficient DECIMAL(10,4) NOT NULL COMMENT '弹力系数 N/mm',
    outer_diameter DECIMAL(10,4) NOT NULL COMMENT '外径尺寸 mm',
    current_line_id BIGINT NOT NULL COMMENT '当前归属产线ID',
    initial_line_id BIGINT NOT NULL COMMENT '初始归属产线ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_spring_code (spring_code),
    INDEX idx_current_line (current_line_id),
    FOREIGN KEY (current_line_id) REFERENCES production_line(id),
    FOREIGN KEY (initial_line_id) REFERENCES production_line(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='弹簧档案表';

CREATE TABLE IF NOT EXISTS transfer_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    spring_id BIGINT NOT NULL COMMENT '弹簧ID',
    spring_code VARCHAR(32) NOT NULL COMMENT '弹簧编号',
    from_line_id BIGINT NOT NULL COMMENT '转出产线ID',
    from_line_name VARCHAR(64) NOT NULL COMMENT '转出产线名称',
    to_line_id BIGINT NOT NULL COMMENT '接收产线ID',
    to_line_name VARCHAR(64) NOT NULL COMMENT '接收产线名称',
    operator VARCHAR(32) NOT NULL COMMENT '操作人',
    operate_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    remark VARCHAR(255) COMMENT '划转备注',
    INDEX idx_spring_id (spring_id),
    INDEX idx_operate_time (operate_time),
    INDEX idx_from_line (from_line_id),
    INDEX idx_to_line (to_line_id),
    FOREIGN KEY (spring_id) REFERENCES spring_archive(id),
    FOREIGN KEY (from_line_id) REFERENCES production_line(id),
    FOREIGN KEY (to_line_id) REFERENCES production_line(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='划转流水记录表';

CREATE TABLE IF NOT EXISTS transfer_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    application_no VARCHAR(32) NOT NULL UNIQUE COMMENT '申请单号',
    applicant VARCHAR(32) NOT NULL COMMENT '申请人',
    apply_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    to_line_id BIGINT NOT NULL COMMENT '目标产线ID',
    to_line_name VARCHAR(64) NOT NULL COMMENT '目标产线名称',
    reason VARCHAR(255) NOT NULL COMMENT '申请原因',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '申请单状态：PENDING-待审批 APPROVED-全部通过 REJECTED-全部驳回 PARTIAL-部分处理',
    INDEX idx_status (status),
    INDEX idx_apply_time (apply_time),
    FOREIGN KEY (to_line_id) REFERENCES production_line(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='划转申请单表';

CREATE TABLE IF NOT EXISTS transfer_application_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    application_id BIGINT NOT NULL COMMENT '划转申请单ID',
    spring_id BIGINT NOT NULL COMMENT '弹簧ID',
    spring_code VARCHAR(32) NOT NULL COMMENT '弹簧编号',
    model VARCHAR(64) NOT NULL COMMENT '弹簧型号',
    elastic_coefficient DECIMAL(10,4) NOT NULL COMMENT '弹力系数 N/mm',
    outer_diameter DECIMAL(10,4) NOT NULL COMMENT '外径尺寸 mm',
    from_line_id BIGINT NOT NULL COMMENT '申请时所在产线ID',
    from_line_name VARCHAR(64) NOT NULL COMMENT '申请时所在产线名称',
    to_line_id BIGINT NOT NULL COMMENT '目标产线ID',
    to_line_name VARCHAR(64) NOT NULL COMMENT '目标产线名称',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '明细状态：PENDING-待审批 APPROVED-已通过 REJECTED-已驳回',
    approver VARCHAR(32) COMMENT '审批人',
    approve_time DATETIME COMMENT '审批时间',
    reject_reason VARCHAR(255) COMMENT '驳回原因',
    transfer_record_id BIGINT COMMENT '审批通过后生成的划转流水ID',
    INDEX idx_application_id (application_id),
    INDEX idx_spring_status (spring_id, status),
    FOREIGN KEY (application_id) REFERENCES transfer_application(id),
    FOREIGN KEY (spring_id) REFERENCES spring_archive(id),
    FOREIGN KEY (from_line_id) REFERENCES production_line(id),
    FOREIGN KEY (to_line_id) REFERENCES production_line(id),
    FOREIGN KEY (transfer_record_id) REFERENCES transfer_record(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='划转申请明细表';

CREATE TABLE IF NOT EXISTS transfer_application_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    application_id BIGINT NOT NULL COMMENT '划转申请单ID',
    action VARCHAR(16) NOT NULL COMMENT '操作类型：SUBMIT-提交申请 APPROVE-审批通过 REJECT-审批驳回',
    operator VARCHAR(32) NOT NULL COMMENT '操作人',
    operate_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    detail VARCHAR(512) COMMENT '操作详情',
    INDEX idx_application_id (application_id),
    FOREIGN KEY (application_id) REFERENCES transfer_application(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='划转申请操作记录表';

CREATE TABLE IF NOT EXISTS transfer_simulation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    simulation_no VARCHAR(32) NOT NULL UNIQUE COMMENT '模拟方案编号',
    operator VARCHAR(32) NOT NULL COMMENT '调度员',
    to_line_id BIGINT NOT NULL COMMENT '拟接收产线ID',
    to_line_name VARCHAR(64) NOT NULL COMMENT '拟接收产线名称',
    remark VARCHAR(255) COMMENT '方案备注',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '方案状态：DRAFT-已保存 ADOPTED-已采用 DISCARDED-已作废',
    estimate_snapshot TEXT COMMENT '保存时的负载预估快照（JSON）',
    application_id BIGINT COMMENT '采用后生成的划转申请单ID',
    application_no VARCHAR(32) COMMENT '采用后生成的划转申请单号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status),
    INDEX idx_create_time (create_time),
    FOREIGN KEY (to_line_id) REFERENCES production_line(id),
    FOREIGN KEY (application_id) REFERENCES transfer_application(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工序调拨模拟方案表';

CREATE TABLE IF NOT EXISTS transfer_simulation_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    simulation_id BIGINT NOT NULL COMMENT '模拟方案ID',
    spring_id BIGINT NOT NULL COMMENT '弹簧ID',
    spring_code VARCHAR(32) NOT NULL COMMENT '弹簧编号',
    model VARCHAR(64) NOT NULL COMMENT '弹簧型号',
    elastic_coefficient DECIMAL(10,4) NOT NULL COMMENT '弹力系数 N/mm',
    from_line_id BIGINT NOT NULL COMMENT '模拟时所在产线ID',
    from_line_name VARCHAR(64) NOT NULL COMMENT '模拟时所在产线名称',
    INDEX idx_simulation_id (simulation_id),
    FOREIGN KEY (simulation_id) REFERENCES transfer_simulation(id),
    FOREIGN KEY (spring_id) REFERENCES spring_archive(id),
    FOREIGN KEY (from_line_id) REFERENCES production_line(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工序调拨模拟方案明细表';

CREATE TABLE IF NOT EXISTS load_alert_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    event_no VARCHAR(32) NOT NULL UNIQUE COMMENT '告警事件编号',
    line_id BIGINT NOT NULL COMMENT '产线ID',
    line_code VARCHAR(32) NOT NULL COMMENT '产线编码（快照）',
    line_name VARCHAR(64) NOT NULL COMMENT '产线名称（快照）',
    alert_level VARCHAR(16) NOT NULL COMMENT '触发级别：WARNING-预警 OVERLOAD-超载',
    snapshot_json TEXT NOT NULL COMMENT '触发时负载快照（LineLoadStats JSON）',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '处置状态：PENDING-待处理 PROCESSING-处置中 RESOLVED-已关闭',
    responsible_person VARCHAR(32) COMMENT '责任人',
    handle_plan VARCHAR(512) COMMENT '处置计划',
    remark VARCHAR(512) COMMENT '备注/处理说明',
    close_type VARCHAR(16) COMMENT '关闭方式：MANUAL-手动关闭 AUTO-恢复正常自动关闭',
    close_remark VARCHAR(512) COMMENT '关闭说明',
    closed_by VARCHAR(32) COMMENT '关闭操作人（系统自动关闭为SYSTEM）',
    trigger_time DATETIME NOT NULL COMMENT '告警触发时间',
    confirm_time DATETIME COMMENT '首次确认时间',
    close_time DATETIME COMMENT '关闭时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    active_line_id BIGINT COMMENT '未关闭事件标记（=line_id），关闭后置空；唯一约束保证同一产线至多一条未关闭事件',
    manual_close_active TINYINT(1) NOT NULL DEFAULT 0 COMMENT '异常持续期间手动关闭的抑制标记，恢复正常后清除',
    UNIQUE KEY uk_active_line (active_line_id),
    INDEX idx_line_trigger (line_id, trigger_time),
    INDEX idx_status (status),
    FOREIGN KEY (line_id) REFERENCES production_line(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产线负载告警事件表';

CREATE TABLE IF NOT EXISTS load_alert_handle_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    event_id BIGINT NOT NULL COMMENT '告警事件ID',
    action VARCHAR(16) NOT NULL COMMENT '操作类型：CONFIRM-确认 PLAN-更新计划 REMARK-备注 RESOLVE-手动关闭 AUTO_RESOLVE-自动关闭',
    operator VARCHAR(32) NOT NULL COMMENT '操作人（系统为SYSTEM）',
    operate_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    result_status VARCHAR(16) NOT NULL COMMENT '操作后事件状态',
    detail VARCHAR(512) COMMENT '操作详情',
    INDEX idx_event_time (event_id, operate_time),
    FOREIGN KEY (event_id) REFERENCES load_alert_event(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='负载告警处置记录表';

INSERT IGNORE INTO production_line
    (line_code, line_name, description, daily_capacity_threshold, elastic_min, elastic_max) VALUES
('LINE-001', '装配一号线', '精密小型件装配线', 2, 0.2000, 1.0000),
('LINE-002', '装配二号线', '中型件标准装配线', 4, 2.0000, 3.0000),
('LINE-003', '装配三号线', '大型件重载装配线', 3, 4.0000, 6.0000),
('LINE-004', '装配四号线', '自动化智能装配线', 5, 0.5000, 3.5000);

INSERT IGNORE INTO spring_archive (spring_code, model, elastic_coefficient, outer_diameter, current_line_id, initial_line_id) VALUES
('SP-2024-0001', 'C-Spring-05', 0.5000, 12.5000, 1, 1),
('SP-2024-0002', 'C-Spring-12', 1.2000, 18.0000, 1, 1),
('SP-2024-0003', 'C-Spring-25', 2.5000, 25.0000, 2, 2),
('SP-2024-0004', 'C-Spring-38', 3.8000, 32.0000, 2, 2),
('SP-2024-0005', 'C-Spring-50', 5.0000, 40.0000, 3, 3),
('SP-2024-0006', 'H-Spring-08', 0.8000, 15.0000, 1, 1),
('SP-2024-0007', 'H-Spring-15', 1.5000, 20.0000, 3, 3),
('SP-2024-0008', 'H-Spring-30', 3.0000, 28.0000, 4, 4),
('SP-2024-0009', 'P-Spring-06', 0.6000, 14.0000, 2, 2),
('SP-2024-0010', 'P-Spring-20', 2.0000, 22.0000, 4, 4),
('SP-2024-0011', 'P-Spring-42', 4.2000, 35.0000, 3, 3),
('SP-2024-0012', 'SP-Spring-10', 1.0000, 16.0000, 4, 4);
