SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS spring_transfer DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE spring_transfer;

CREATE TABLE IF NOT EXISTS production_line (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    line_code VARCHAR(32) NOT NULL UNIQUE COMMENT '产线编码',
    line_name VARCHAR(64) NOT NULL COMMENT '产线名称',
    description VARCHAR(255) COMMENT '产线描述',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_line_code (line_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产产线表';

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

INSERT IGNORE INTO production_line (line_code, line_name, description) VALUES
('LINE-001', '装配一号线', '精密小型件装配线'),
('LINE-002', '装配二号线', '中型件标准装配线'),
('LINE-003', '装配三号线', '大型件重载装配线'),
('LINE-004', '装配四号线', '自动化智能装配线');

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
